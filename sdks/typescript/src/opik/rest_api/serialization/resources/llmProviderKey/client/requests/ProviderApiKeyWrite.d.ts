/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../../../../index";
import * as OpikApi from "../../../../../api/index";
import * as core from "../../../../../core";
export declare const ProviderApiKeyWrite: core.serialization.Schema<serializers.ProviderApiKeyWrite.Raw, OpikApi.ProviderApiKeyWrite>;
export declare namespace ProviderApiKeyWrite {
    interface Raw {
        api_key: string;
    }
}
