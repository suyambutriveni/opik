package com.comet.opik.api.resources.v1.priv;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.Dataset;
import com.comet.opik.api.DatasetCriteria;
import com.comet.opik.api.DatasetIdentifier;
import com.comet.opik.api.DatasetItem;
import com.comet.opik.api.DatasetItemBatch;
import com.comet.opik.api.DatasetItemSearchCriteria;
import com.comet.opik.api.DatasetItemStreamRequest;
import com.comet.opik.api.DatasetItemsDelete;
import com.comet.opik.api.DatasetUpdate;
import com.comet.opik.api.ExperimentItem;
import com.comet.opik.domain.DatasetItemService;
import com.comet.opik.domain.DatasetService;
import com.comet.opik.domain.FeedbackScoreDAO;
import com.comet.opik.domain.IdGenerator;
import com.comet.opik.infrastructure.auth.RequestContext;
import com.comet.opik.utils.AsyncUtils;
import com.comet.opik.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.jersey.errors.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ChunkedOutput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.comet.opik.api.Dataset.DatasetPage;
import static com.comet.opik.utils.AsyncUtils.setRequestContext;

@Path("/v1/private/datasets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Tag(name = "Datasets", description = "Dataset resources")
public class DatasetsResource {

    private static final String STREAM_ERROR_LOG = "Error while streaming dataset items";

    private static final TypeReference<List<UUID>> LIST_UUID_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final @NonNull DatasetService service;
    private final @NonNull DatasetItemService itemService;
    private final @NonNull Provider<RequestContext> requestContext;
    private final @NonNull IdGenerator idGenerator;

    @GET
    @Path("/{id}")
    @Operation(operationId = "getDatasetById", summary = "Get dataset by id", description = "Get dataset by id", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset resource", content = @Content(schema = @Schema(implementation = Dataset.class)))
    })
    @JsonView(Dataset.View.Public.class)
    public Response getDatasetById(@PathParam("id") UUID id) {

        return Response.ok().entity(service.findById(id)).build();
    }

    @GET
    @Operation(operationId = "findDatasets", summary = "Find datasets", description = "Find datasets", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset resource", content = @Content(schema = @Schema(implementation = DatasetPage.class)))
    })
    @JsonView(Dataset.View.Public.class)
    public Response findDatasets(
            @QueryParam("page") @Min(1) @DefaultValue("1") int page,
            @QueryParam("size") @Min(1) @DefaultValue("10") int size,
            @QueryParam("name") String name) {

        var criteria = DatasetCriteria.builder()
                .name(name)
                .build();

        return Response.ok(service.find(page, size, criteria)).build();
    }

    @POST
    @Operation(operationId = "createDataset", summary = "Create dataset", description = "Create dataset", responses = {
            @ApiResponse(responseCode = "201", description = "Created", headers = {
                    @Header(name = "Location", required = true, example = "${basePath}/api/v1/private/datasets/{id}", schema = @Schema(implementation = String.class))
            })
    })
    public Response createDataset(
            @RequestBody(content = @Content(schema = @Schema(implementation = Dataset.class))) @JsonView(Dataset.View.Write.class) @NotNull @Valid Dataset dataset,
            @Context UriInfo uriInfo) {

        URI uri = uriInfo.getAbsolutePathBuilder().path("/%s".formatted(service.save(dataset).id().toString())).build();
        return Response.created(uri).build();
    }

    @PUT
    @Path("{id}")
    @Operation(operationId = "updateDataset", summary = "Update dataset by id", description = "Update dataset by id", responses = {
            @ApiResponse(responseCode = "204", description = "No content"),
    })
    public Response updateDataset(@PathParam("id") UUID id,
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetUpdate.class))) @NotNull @Valid DatasetUpdate datasetUpdate) {

        service.update(id, datasetUpdate);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(operationId = "deleteDataset", summary = "Delete dataset by id", description = "Delete dataset by id", responses = {
            @ApiResponse(responseCode = "204", description = "No content"),
    })
    public Response deleteDataset(@PathParam("id") UUID id) {

        service.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/delete")
    @Operation(operationId = "deleteDatasetByName", summary = "Delete dataset by name", description = "Delete dataset by name", responses = {
            @ApiResponse(responseCode = "204", description = "No content"),
    })
    public Response deleteDatasetByName(
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetIdentifier.class))) @NotNull @Valid DatasetIdentifier identifier) {

        service.delete(identifier);
        return Response.noContent().build();
    }

    @POST
    @Path("/retrieve")
    @Operation(operationId = "getDatasetByIdentifier", summary = "Get dataset by name", description = "Get dataset by name", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset resource", content = @Content(schema = @Schema(implementation = Dataset.class))),
    })
    @JsonView(Dataset.View.Public.class)
    public Response getDatasetByIdentifier(
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetIdentifier.class))) @NotNull @Valid DatasetIdentifier identifier) {

        String workspaceId = requestContext.get().getWorkspaceId();
        String name = identifier.datasetName();

        return Response.ok(service.findByName(workspaceId, name)).build();
    }

    // Dataset Item Resources

    @GET
    @Path("/items/{itemId}")
    @Operation(operationId = "getDatasetItemById", summary = "Get dataset item by id", description = "Get dataset item by id", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset item resource", content = @Content(schema = @Schema(implementation = DatasetItem.class)))
    })
    @JsonView(DatasetItem.View.Public.class)
    public Response getDatasetItemById(@PathParam("itemId") @NotNull UUID itemId) {

        return Response.ok(itemService.get(itemId)
                .contextWrite(ctx -> setRequestContext(ctx, requestContext))
                .block()).build();
    }

    @GET
    @Path("/{id}/items")
    @Operation(operationId = "getDatasetItems", summary = "Get dataset items", description = "Get dataset items", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset items resource", content = @Content(schema = @Schema(implementation = DatasetItem.DatasetItemPage.class)))
    })
    @JsonView(DatasetItem.View.Public.class)
    public Response getDatasetItems(
            @PathParam("id") UUID id,
            @QueryParam("page") @Min(1) @DefaultValue("1") int page,
            @QueryParam("size") @Min(1) @DefaultValue("10") int size) {

        return Response.ok(itemService.getItems(id, page, size)
                .contextWrite(ctx -> setRequestContext(ctx, requestContext))
                .block())
                .build();
    }

    @POST
    @Path("/items/stream")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(operationId = "streamDatasetItems", summary = "Stream dataset items", description = "Stream dataset items", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset items stream or error during process", content = @Content(array = @ArraySchema(schema = @Schema(anyOf = {
                    DatasetItem.class,
                    ErrorMessage.class
            }), maxItems = 1000)))
    })
    public ChunkedOutput<JsonNode> streamDatasetItems(
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetItemStreamRequest.class))) @NotNull @Valid DatasetItemStreamRequest request) {

        return getOutputStream(request, request.steamLimit());
    }

    private ChunkedOutput<JsonNode> getOutputStream(DatasetItemStreamRequest request, int limit) {

        final ChunkedOutput<JsonNode> outputStream = new ChunkedOutput<>(JsonNode.class, "\r\n");

        String workspaceId = requestContext.get().getWorkspaceId();
        String userName = requestContext.get().getUserName();
        String workspaceName = requestContext.get().getWorkspaceName();

        Schedulers
                .boundedElastic()
                .schedule(() -> Mono.fromCallable(() -> service.findByName(workspaceId, request.datasetName()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(dataset -> itemService.getItems(dataset.id(), limit, request.lastRetrievedId()))
                        .doOnNext(item -> sendDatasetItems(item, outputStream))
                        .onErrorResume(ex -> errorHandling(ex, outputStream))
                        .doFinally(signalType -> closeOutput(outputStream))
                        .contextWrite(ctx -> ctx.put(RequestContext.USER_NAME, userName)
                                .put(RequestContext.WORKSPACE_NAME, workspaceName)
                                .put(RequestContext.WORKSPACE_ID, workspaceId))
                        .subscribe());

        return outputStream;
    }

    private void closeOutput(ChunkedOutput<JsonNode> outputStream) {
        try {
            outputStream.close();
        } catch (IOException e) {
            log.error(STREAM_ERROR_LOG, e);
        }
    }

    private <T> Flux<T> errorHandling(Throwable ex, ChunkedOutput<JsonNode> outputStream) {
        if (ex instanceof TimeoutException timeoutException) {
            try {
                writeError(outputStream, "Streaming operation timed out");
            } catch (IOException ioe) {
                log.warn("Failed to send error to client", ioe);
            }

            return Flux.error(timeoutException);
        }

        return Flux.error(ex);
    }

    private void writeError(ChunkedOutput<JsonNode> outputStream, String errorMessage) throws IOException {
        outputStream.write(JsonUtils.readTree(new ErrorMessage(500, errorMessage)));
    }

    private void sendDatasetItems(DatasetItem item, ChunkedOutput<JsonNode> writer) {
        try {
            writer.write(JsonUtils.readTree(item));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PUT
    @Path("/items")
    @Operation(operationId = "createOrUpdateDatasetItems", summary = "Create/update dataset items", description = "Create/update dataset items based on dataset item id", responses = {
            @ApiResponse(responseCode = "204", description = "No content"),
    })
    public Response createDatasetItems(
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetItemBatch.class))) @JsonView({
                    DatasetItem.View.Write.class}) @NotNull @Valid DatasetItemBatch batch) {

        // Generate ids for items without ids before the retryable operation
        List<DatasetItem> items = batch.items().stream().map(item -> {
            if (item.id() == null) {
                return item.toBuilder().id(idGenerator.generateId()).build();
            }
            return item;
        }).toList();

        itemService.save(new DatasetItemBatch(batch.datasetName(), batch.datasetId(), items))
                .contextWrite(ctx -> setRequestContext(ctx, requestContext))
                .retryWhen(AsyncUtils.handleConnectionError())
                .block();

        return Response.noContent().build();
    }

    @POST
    @Path("/items/delete")
    @Operation(operationId = "deleteDatasetItems", summary = "Delete dataset items", description = "Delete dataset items", responses = {
            @ApiResponse(responseCode = "204", description = "No content"),
    })
    public Response deleteDatasetItems(
            @RequestBody(content = @Content(schema = @Schema(implementation = DatasetItemsDelete.class))) @NotNull @Valid DatasetItemsDelete request) {

        itemService.delete(request.itemIds())
                .contextWrite(ctx -> setRequestContext(ctx, requestContext))
                .block();
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/items/experiments/items")
    @Operation(operationId = "findDatasetItemsWithExperimentItems", summary = "Find dataset items with experiment items", description = "Find dataset items with experiment items", responses = {
            @ApiResponse(responseCode = "200", description = "Dataset item resource", content = @Content(schema = @Schema(implementation = DatasetItem.DatasetItemPage.class)))
    })
    @JsonView(ExperimentItem.View.Compare.class)
    public Response findDatasetItemsWithExperimentItems(
            @PathParam("id") UUID datasetId,
            @QueryParam("page") @Min(1) @DefaultValue("1") int page,
            @QueryParam("size") @Min(1) @DefaultValue("10") int size,
            @QueryParam("experiment_ids") @NotNull @NotBlank String experimentIdsQueryParam,
            @QueryParam("filters") String filters) {

        var experimentIds = getExperimentIds(experimentIdsQueryParam);

        var datasetItemSearchCriteria = DatasetItemSearchCriteria.builder()
                .datasetId(datasetId)
                .experimentIds(experimentIds)
                .entityType(FeedbackScoreDAO.EntityType.TRACE)
                .build();

        log.info("Finding dataset items with experiment items by '{}'", datasetItemSearchCriteria);
        var datasetItemPage = itemService.getItems(page, size, datasetItemSearchCriteria)
                .contextWrite(ctx -> setRequestContext(ctx, requestContext))
                .block();
        log.info("Found dataset items with experiment items by '{}', count '{}'",
                datasetItemSearchCriteria, datasetItemPage.content().size());
        return Response.ok(datasetItemPage).build();
    }

    private Set<UUID> getExperimentIds(String experimentIds) {
        var message = "Invalid query param experiment ids '%s'".formatted(experimentIds);
        try {
            return JsonUtils.readValue(experimentIds, LIST_UUID_TYPE_REFERENCE)
                    .stream()
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException exception) {
            log.warn(message, exception);
            throw new BadRequestException(message, exception);
        }
    }
}