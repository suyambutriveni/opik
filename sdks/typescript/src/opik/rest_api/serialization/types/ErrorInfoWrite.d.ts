/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
export declare const ErrorInfoWrite: core.serialization.ObjectSchema<serializers.ErrorInfoWrite.Raw, OpikApi.ErrorInfoWrite>;
export declare namespace ErrorInfoWrite {
    interface Raw {
        exception_type: string;
        message?: string | null;
        traceback: string;
    }
}
