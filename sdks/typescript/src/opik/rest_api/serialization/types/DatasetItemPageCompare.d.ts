/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
import { DatasetItemCompare } from "./DatasetItemCompare";
import { ColumnCompare } from "./ColumnCompare";
export declare const DatasetItemPageCompare: core.serialization.ObjectSchema<serializers.DatasetItemPageCompare.Raw, OpikApi.DatasetItemPageCompare>;
export declare namespace DatasetItemPageCompare {
    interface Raw {
        content?: DatasetItemCompare.Raw[] | null;
        page?: number | null;
        size?: number | null;
        total?: number | null;
        columns?: ColumnCompare.Raw[] | null;
    }
}