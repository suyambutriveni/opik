import uniqid from "uniqid";
import flatten from "lodash/flatten";
import { Filter } from "@/types/filters";
import { COLUMN_TYPE } from "@/types/shared";
import { makeEndOfDay, makeStartOfDay } from "@/lib/date";

export const isFilterValid = (filter: Filter) => {
  return (
    (filter.type === COLUMN_TYPE.dictionary ||
    filter.type === COLUMN_TYPE.numberDictionary
      ? filter.key !== ""
      : true) && filter.value !== ""
  );
};

export const createEmptyFilter = () => {
  return {
    id: uniqid(),
    field: "",
    type: "",
    operator: "",
    key: "",
    value: "",
  } as Filter;
};

export const generateSearchByIDFilters = (search?: string) => {
  if (!search) return undefined;

  return [
    {
      id: uniqid(),
      field: "id",
      type: COLUMN_TYPE.string,
      operator: "=",
      key: "",
      value: search,
    },
  ] as Filter[];
};

const processTimeFilter: (filter: Filter) => Filter | Filter[] = (filter) => {
  switch (filter.operator) {
    case "=":
      return [
        {
          ...filter,
          operator: ">",
          value: makeStartOfDay(filter.value as string),
        },
        {
          ...filter,
          operator: "<",
          value: makeEndOfDay(filter.value as string),
        },
      ];
    case ">":
    case "<=":
      return [
        {
          ...filter,
          value: makeEndOfDay(filter.value as string),
        },
      ];
    case "<":
    case ">=":
      return [
        {
          ...filter,
          value: makeStartOfDay(filter.value as string),
        },
      ];
    default:
      return filter;
  }
};

const processFiltersArray = (filters: Filter[]) => {
  return flatten(
    filters.map((filter) => {
      if (filter.type === COLUMN_TYPE.time) {
        return processTimeFilter(filter);
      }

      return filter;
    }),
  );
};

export const processFilters = (
  filters?: Filter[],
  additionalFilters?: Filter[],
) => {
  const retVal: {
    filters?: string;
  } = {};
  const processedFilters: Filter[] = [];

  if (filters && filters.length > 0) {
    processFiltersArray(filters).forEach((f) => processedFilters.push(f));
  }

  if (additionalFilters && additionalFilters.length > 0) {
    processFiltersArray(additionalFilters).forEach((f) =>
      processedFilters.push(f),
    );
  }

  if (processedFilters.length > 0) {
    retVal.filters = JSON.stringify(processedFilters);
  }

  return retVal;
};