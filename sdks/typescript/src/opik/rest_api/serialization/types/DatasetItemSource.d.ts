/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
export declare const DatasetItemSource: core.serialization.Schema<serializers.DatasetItemSource.Raw, OpikApi.DatasetItemSource>;
export declare namespace DatasetItemSource {
    type Raw = "manual" | "trace" | "span" | "sdk";
}
