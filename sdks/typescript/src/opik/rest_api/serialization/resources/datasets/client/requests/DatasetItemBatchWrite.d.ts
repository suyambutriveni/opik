/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../../../../index";
import * as OpikApi from "../../../../../api/index";
import * as core from "../../../../../core";
import { DatasetItemWrite } from "../../../../types/DatasetItemWrite";
export declare const DatasetItemBatchWrite: core.serialization.Schema<serializers.DatasetItemBatchWrite.Raw, OpikApi.DatasetItemBatchWrite>;
export declare namespace DatasetItemBatchWrite {
    interface Raw {
        dataset_name?: string | null;
        dataset_id?: string | null;
        items: DatasetItemWrite.Raw[];
    }
}