package com.comet.opik.domain;

import com.comet.opik.api.DatasetItem;
import com.comet.opik.api.DatasetItemSearchCriteria;
import com.comet.opik.api.DatasetItemSource;
import com.comet.opik.api.ExperimentItem;
import com.comet.opik.api.FeedbackScore;
import com.comet.opik.api.ScoreSource;
import com.comet.opik.infrastructure.db.TransactionTemplate;
import com.comet.opik.utils.AsyncUtils;
import com.comet.opik.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.ImplementedBy;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.stringtemplate.v4.ST;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.comet.opik.api.DatasetItem.DatasetItemPage;
import static com.comet.opik.domain.AsyncContextUtils.bindWorkspaceIdToFlux;
import static com.comet.opik.utils.AsyncUtils.makeFluxContextAware;
import static com.comet.opik.utils.AsyncUtils.makeMonoContextAware;
import static com.comet.opik.utils.ValidationUtils.CLICKHOUSE_FIXED_STRING_UUID_FIELD_NULL_VALUE;

@ImplementedBy(DatasetItemDAOImpl.class)
public interface DatasetItemDAO {
    Mono<Long> save(UUID datasetId, List<DatasetItem> batch);

    Mono<Long> delete(List<UUID> ids);

    Mono<DatasetItemPage> getItems(UUID datasetId, int page, int size);

    Mono<DatasetItemPage> getItems(DatasetItemSearchCriteria datasetItemSearchCriteria, int page, int size);

    Mono<DatasetItem> get(UUID id);

    Flux<DatasetItem> getItems(UUID datasetId, int limit, UUID lastRetrievedId);

    Flux<WorkspaceAndResourceId> getDatasetItemWorkspace(Set<UUID> datasetItemIds);

}

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
class DatasetItemDAOImpl implements DatasetItemDAO {

    /**
     * This query is used to insert/update a dataset item into the database.
     * 1. The query uses a multiIf function to determine the value of the dataset_id field and validate if it matches with the previous value.
     * 2. The query uses a multiIf function to determine the value of the created_at field and validate if it matches with the previous value to avoid duplication of rows.
     * */
    private static final String INSERT_DATASET_ITEM = """
                INSERT INTO dataset_items (
                    id,
                    dataset_id,
                    source,
                    trace_id,
                    span_id,
                    input,
                    expected_output,
                    metadata,
                    created_at,
                    workspace_id,
                    created_by,
                    last_updated_by
                )
                SELECT
                    new.id,
                    multiIf(
                        LENGTH(CAST(old.dataset_id AS Nullable(String))) > 0 AND notEquals(old.dataset_id, new.dataset_id), leftPad('', 40, '*'),
                        LENGTH(CAST(old.dataset_id AS Nullable(String))) > 0, old.dataset_id,
                        new.dataset_id
                    ) as dataset_id,
                    new.source,
                    new.trace_id,
                    new.span_id,
                    new.input,
                    new.expected_output,
                    new.metadata,
                    multiIf(
                        notEquals(old.created_at, toDateTime64('1970-01-01 00:00:00.000', 9)), old.created_at,
                        new.created_at
                    ) as created_at,
                    multiIf(
                        LENGTH(old.workspace_id) > 0 AND notEquals(old.workspace_id, new.workspace_id), CAST(leftPad('', 40, '*') AS FixedString(19)),
                        LENGTH(old.workspace_id) > 0, old.workspace_id,
                        new.workspace_id
                    ) as workspace_id,
                    if(
                        LENGTH(old.created_by) > 0, old.created_by,
                        new.created_by
                    ) as created_by,
                    new.last_updated_by
                FROM (
                    SELECT
                        :id AS id,
                        :datasetId AS dataset_id,
                        :source AS source,
                        :traceId AS trace_id,
                        :spanId AS span_id,
                        :input AS input,
                        :expectedOutput AS expected_output,
                        :metadata AS metadata,
                        now64(9) AS created_at,
                        :workspace_id AS workspace_id,
                        :createdBy AS created_by,
                        :lastUpdatedBy AS last_updated_by
                ) AS new
                LEFT JOIN (
                    SELECT
                        *
                    FROM dataset_items
                    WHERE id = :id
                    ORDER BY last_updated_at DESC
                    LIMIT 1 BY id
                ) AS old
                ON old.id = new.id
                ;
            """;

    private static final String SELECT_DATASET_ITEM = """
                SELECT
                    *,
                    null as experiment_items_array
                FROM dataset_items
                WHERE id = :id
                AND workspace_id = :workspace_id
                ORDER BY last_updated_at DESC
                LIMIT 1 BY id
                ;
            """;

    private static final String SELECT_DATASET_ITEMS_STREAM = """
                SELECT
                    *,
                    null as experiment_items_array
                FROM dataset_items
                WHERE dataset_id = :datasetId
                AND workspace_id = :workspace_id
                <if(lastRetrievedId)>AND id \\< :lastRetrievedId <endif>
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
                LIMIT :limit
                ;
            """;

    private static final String DELETE_DATASET_ITEM = """
                DELETE FROM dataset_items
                WHERE id IN :ids
                AND workspace_id = :workspace_id
                ;
            """;

    private static final String SELECT_DATASET_ITEMS = """
                SELECT
                    *,
                    null as experiment_items_array
                FROM dataset_items
                WHERE dataset_id = :datasetId
                AND workspace_id = :workspace_id
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
                LIMIT :limit OFFSET :offset
                ;
            """;

    private static final String SELECT_DATASET_ITEMS_COUNT = """
                SELECT
                    count(id) as count
                FROM (
                    SELECT
                        id
                    FROM dataset_items
                    WHERE dataset_id = :datasetId
                    AND workspace_id = :workspace_id
                    ORDER BY id DESC, last_updated_at DESC
                    LIMIT 1 BY id
                ) as lastRows
                ;
            """;

    private static final String SELECT_DATASET_WORKSPACE_ITEMS = """
                SELECT
                    id, workspace_id
                FROM dataset_items
                WHERE id IN :datasetItemIds
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
                ;
            """;

    /**
     * Gets the following relationships:
     * - dataset_item - experiment_items -> 1:N
     * - experiment_item - trace -> 1:1
     * - trace - feedback_scores -> 1:N
     * And groups everything together resembling the following rough JSON structure:
     *  {
     *      "dataset_item" : {
     *          "id": "some_id",
     *          ...
     *          "experiment_items": [
     *            {
     *                "id": "some_id",
     *                "input": "trace_input_value",
     *                "output": "trace_output_value",
     *                "feedback_scores": [
     *                  {
     *                    "name": "some_name",
     *                    ...
     *                  },
     *                  ...
     *                ]
     *            },
     *            ...
     *          ]
     *          "
     *      }
     *  }
     */
    private static final String SELECT_DATASET_ITEMS_WITH_EXPERIMENT_ITEMS = """
            SELECT
                di.id as id,
                di.input as input,
                di.expected_output as expected_output,
                di.metadata as metadata,
                di.trace_id as trace_id,
                di.span_id as span_id,
                di.source as source,
                di.created_at as created_at,
                di.last_updated_at as last_updated_at,
                di.created_by as created_by,
                di.last_updated_by as last_updated_by,
                groupArray(tuple(
                    ei.id,
                    ei.experiment_id,
                    ei.dataset_item_id,
                    ei.trace_id,
                    t.input,
                    t.output,
                    t.feedback_scores_array,
                    ei.created_at,
                    ei.last_updated_at,
                    ei.created_by,
                    ei.last_updated_by
                )) as experiment_items_array
            FROM (
                SELECT
                    *
                FROM dataset_items
                WHERE dataset_id = :datasetId
                AND workspace_id = :workspace_id
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
            ) as di
            LEFT JOIN (
                SELECT
                    *
                FROM experiment_items
                WHERE experiment_id in :experiment_ids
                AND workspace_id = :workspace_id
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
            ) as ei ON di.id = ei.dataset_item_id
            LEFT JOIN (
                SELECT
                    id,
                    input,
                    output,
                    groupArray(tuple(
                        fs.entity_id,
                        fs.name,
                        fs.category_name,
                        fs.value,
                        fs.reason,
                        fs.source
                    )) as feedback_scores_array
                FROM traces
                LEFT JOIN (
                    SELECT
                        *
                    FROM feedback_scores
                    WHERE entity_type = :entity_type
                    AND workspace_id = :workspace_id
                    ORDER BY entity_id DESC, last_updated_at DESC
                    LIMIT 1 BY entity_id, name
                ) as fs ON id = fs.entity_id
                GROUP BY
                    id,
                    input,
                    output,
                    last_updated_at
                ORDER BY id DESC, last_updated_at DESC
                LIMIT 1 BY id
            ) as t ON ei.trace_id = t.id
            GROUP BY
                di.id,
                di.input,
                di.expected_output,
                di.metadata,
                di.trace_id,
                di.span_id,
                di.source,
                di.created_at,
                di.last_updated_at,
                di.created_by,
                di.last_updated_by
            ORDER BY di.id DESC, di.last_updated_at DESC
            LIMIT :limit OFFSET :offset
            ;
            """;

    private final @NonNull TransactionTemplate asyncTemplate;

    @Override
    public Mono<Long> save(@NonNull UUID datasetId, @NonNull List<DatasetItem> items) {

        if (items.isEmpty()) {
            return Mono.empty();
        }

        return inset(datasetId, items)
                .retryWhen(AsyncUtils.handleConnectionError());
    }

    private Mono<Long> inset(UUID datasetId, List<DatasetItem> items) {
        return asyncTemplate.nonTransaction(connection -> {

            var statement = connection.createStatement(INSERT_DATASET_ITEM);

            return mapAndInsert(datasetId, items, statement)
                    .flatMap(Result::getRowsUpdated)
                    .reduce(0L, Long::sum);
        });
    }

    private Flux<? extends Result> mapAndInsert(UUID datasetId, List<DatasetItem> items, Statement statement) {
        return makeFluxContextAware((userName, workspaceName, workspaceId) -> {

            for (Iterator<DatasetItem> iterator = items.iterator(); iterator.hasNext();) {
                var item = iterator.next();

                statement.bind("id", item.id())
                        .bind("datasetId", datasetId)
                        .bind("input", item.input().toString())
                        .bind("source", item.source().getValue())
                        .bind("traceId", getOrDefault(item.traceId()))
                        .bind("spanId", getOrDefault(item.spanId()))
                        .bind("expectedOutput", getOrDefault(item.expectedOutput()))
                        .bind("metadata", getOrDefault(item.metadata()))
                        .bind("workspace_id", workspaceId)
                        .bind("createdBy", userName)
                        .bind("lastUpdatedBy", userName);

                if (iterator.hasNext()) {
                    statement.add();
                }
            }

            statement.fetchSize(items.size());

            return Flux.from(statement.execute());
        });
    }

    private String getOrDefault(JsonNode jsonNode) {
        return Optional.ofNullable(jsonNode).map(JsonNode::toString).orElse("");
    }

    private String getOrDefault(UUID value) {
        return Optional.ofNullable(value).map(UUID::toString).orElse("");
    }

    private Publisher<DatasetItem> mapItem(Result results) {
        return results.map((row, rowMetadata) -> DatasetItem.builder()
                .id(row.get("id", UUID.class))
                .input(Optional.ofNullable(row.get("input", String.class))
                        .filter(s -> !s.isBlank())
                        .map(JsonUtils::getJsonNodeFromString).orElse(null))
                .expectedOutput(Optional.ofNullable(row.get("expected_output", String.class))
                        .filter(s -> !s.isBlank())
                        .map(JsonUtils::getJsonNodeFromString).orElse(null))
                .metadata(Optional.ofNullable(row.get("metadata", String.class))
                        .filter(s -> !s.isBlank())
                        .map(JsonUtils::getJsonNodeFromString).orElse(null))
                .source(DatasetItemSource.fromString(row.get("source", String.class)))
                .traceId(Optional.ofNullable(row.get("trace_id", String.class))
                        .filter(s -> !s.isBlank())
                        .map(UUID::fromString)
                        .orElse(null))
                .spanId(Optional.ofNullable(row.get("span_id", String.class))
                        .filter(s -> !s.isBlank())
                        .map(UUID::fromString)
                        .orElse(null))
                .experimentItems(getExperimentItems(row.get("experiment_items_array", List[].class)))
                .lastUpdatedAt(row.get("last_updated_at", Instant.class))
                .createdAt(row.get("created_at", Instant.class))
                .createdBy(row.get("created_by", String.class))
                .lastUpdatedBy(row.get("last_updated_by", String.class))
                .build());
    }

    private List<ExperimentItem> getExperimentItems(List[] experimentItemsArrays) {
        if (ArrayUtils.isEmpty(experimentItemsArrays)) {
            return null;
        }

        var experimentItems = Arrays.stream(experimentItemsArrays)
                .filter(experimentItem -> CollectionUtils.isNotEmpty(experimentItem) &&
                        !CLICKHOUSE_FIXED_STRING_UUID_FIELD_NULL_VALUE.equals(experimentItem.get(2).toString()))
                .map(experimentItem -> ExperimentItem.builder()
                        .id(UUID.fromString(experimentItem.get(0).toString()))
                        .experimentId(UUID.fromString(experimentItem.get(1).toString()))
                        .datasetItemId(UUID.fromString(experimentItem.get(2).toString()))
                        .traceId(UUID.fromString(experimentItem.get(3).toString()))
                        .input(getJsonNodeOrNull(experimentItem.get(4)))
                        .output(getJsonNodeOrNull(experimentItem.get(5)))
                        .feedbackScores(getFeedbackScores(experimentItem.get(6)))
                        .createdAt(Instant.parse(experimentItem.get(7).toString()))
                        .lastUpdatedAt(Instant.parse(experimentItem.get(8).toString()))
                        .createdBy(experimentItem.get(9).toString())
                        .lastUpdatedBy(experimentItem.get(10).toString())
                        .build())
                .toList();

        return experimentItems.isEmpty() ? null : experimentItems;
    }

    private JsonNode getJsonNodeOrNull(Object field) {
        if (null == field || StringUtils.isBlank(field.toString())) {
            return null;
        }
        return JsonUtils.getJsonNodeFromString(field.toString());
    }

    private List<FeedbackScore> getFeedbackScores(Object feedbackScoresRaw) {
        if (feedbackScoresRaw instanceof List[] feedbackScoresArray) {
            var feedbackScores = Arrays.stream(feedbackScoresArray)
                    .filter(feedbackScore -> CollectionUtils.isNotEmpty(feedbackScore) &&
                            !CLICKHOUSE_FIXED_STRING_UUID_FIELD_NULL_VALUE.equals(feedbackScore.get(0).toString()))
                    .map(feedbackScore -> FeedbackScore.builder()
                            .name(feedbackScore.get(1).toString())
                            .categoryName(Optional.ofNullable(feedbackScore.get(2)).map(Object::toString).orElse(null))
                            .value(new BigDecimal(feedbackScore.get(3).toString()))
                            .reason(Optional.ofNullable(feedbackScore.get(4)).map(Object::toString).orElse(null))
                            .source(ScoreSource.fromString(feedbackScore.get(5).toString()))
                            .build())
                    .toList();
            return feedbackScores.isEmpty() ? null : feedbackScores;
        }
        return null;
    }

    @Override
    public Mono<DatasetItem> get(@NonNull UUID id) {
        return asyncTemplate.nonTransaction(connection -> {

            Statement statement = connection.createStatement(SELECT_DATASET_ITEM)
                    .bind("id", id);

            return makeFluxContextAware(bindWorkspaceIdToFlux(statement))
                    .flatMap(this::mapItem)
                    .singleOrEmpty();
        });
    }

    @Override
    public Flux<DatasetItem> getItems(@NonNull UUID datasetId, int limit, UUID lastRetrievedId) {
        ST template = new ST(SELECT_DATASET_ITEMS_STREAM);

        if (lastRetrievedId != null) {
            template.add("lastRetrievedId", lastRetrievedId);
        }

        return asyncTemplate.stream(connection -> {

            var statement = connection.createStatement(template.render())
                    .bind("datasetId", datasetId)
                    .bind("limit", limit);

            if (lastRetrievedId != null) {
                statement.bind("lastRetrievedId", lastRetrievedId);
            }

            return makeFluxContextAware(bindWorkspaceIdToFlux(statement))
                    .flatMap(this::mapItem);
        });
    }

    @Override
    public Flux<WorkspaceAndResourceId> getDatasetItemWorkspace(@NonNull Set<UUID> datasetItemIds) {

        if (datasetItemIds.isEmpty()) {
            return Flux.empty();
        }

        return asyncTemplate.stream(connection -> {

            var statement = connection.createStatement(SELECT_DATASET_WORKSPACE_ITEMS)
                    .bind("datasetItemIds", datasetItemIds.toArray(UUID[]::new));

            return Flux.from(statement.execute())
                    .flatMap(result -> result.map((row, rowMetadata) -> new WorkspaceAndResourceId(
                            row.get("workspace_id", String.class),
                            row.get("id", UUID.class))));
        });
    }

    @Override
    public Mono<Long> delete(@NonNull List<UUID> ids) {
        if (ids.isEmpty()) {
            return Mono.empty();
        }

        return asyncTemplate.nonTransaction(connection -> {

            Statement statement = connection.createStatement(DELETE_DATASET_ITEM);

            return bindAndDelete(ids, statement)
                    .flatMap(Result::getRowsUpdated)
                    .reduce(0L, Long::sum);
        });
    }

    private Flux<? extends Result> bindAndDelete(List<UUID> ids, Statement statement) {

        statement.bind("ids", ids.stream().map(UUID::toString).toArray(String[]::new));

        return makeFluxContextAware(bindWorkspaceIdToFlux(statement));
    }

    @Override
    public Mono<DatasetItemPage> getItems(@NonNull UUID datasetId, int page, int size) {
        return makeMonoContextAware((userName, workspaceName,
                workspaceId) -> asyncTemplate.nonTransaction(connection -> Flux
                        .from(connection.createStatement(SELECT_DATASET_ITEMS_COUNT)
                                .bind("datasetId", datasetId)
                                .bind("workspace_id", workspaceId)
                                .execute())
                        .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, Long.class)))
                        .reduce(0L, Long::sum)
                        .flatMap(count -> Flux.from(connection.createStatement(SELECT_DATASET_ITEMS)
                                .bind("workspace_id", workspaceId)
                                .bind("datasetId", datasetId)
                                .bind("limit", size)
                                .bind("offset", (page - 1) * size)
                                .execute())
                                .flatMap(this::mapItem)
                                .collectList()
                                .flatMap(items -> Mono.just(new DatasetItemPage(items, page, items.size(), count))))));
    }

    @Override
    public Mono<DatasetItemPage> getItems(@NonNull DatasetItemSearchCriteria datasetItemSearchCriteria, int page,
            int size) {

        return makeMonoContextAware(
                (userName, workspaceName,
                        workspaceId) -> asyncTemplate
                                .nonTransaction(connection -> Flux
                                        .from(connection.createStatement(SELECT_DATASET_ITEMS_COUNT)
                                                .bind("datasetId", datasetItemSearchCriteria.datasetId())
                                                .bind("workspace_id", workspaceId)
                                                .execute())
                                        .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, Long.class)))
                                        .reduce(0L, Long::sum)
                                        .flatMap(
                                                count -> Flux
                                                        .from(connection
                                                                .createStatement(
                                                                        SELECT_DATASET_ITEMS_WITH_EXPERIMENT_ITEMS)
                                                                .bind("datasetId",
                                                                        datasetItemSearchCriteria.datasetId())
                                                                .bind("experiment_ids",
                                                                        datasetItemSearchCriteria.experimentIds())
                                                                .bind("entity_type",
                                                                        datasetItemSearchCriteria.entityType()
                                                                                .getType())
                                                                .bind("workspace_id", workspaceId)
                                                                .bind("limit", size)
                                                                .bind("offset", (page - 1) * size)
                                                                .execute())
                                                        .flatMap(this::mapItem)
                                                        .collectList()
                                                        .flatMap(items -> Mono.just(new DatasetItemPage(items, page,
                                                                items.size(), count))))));
    }
}