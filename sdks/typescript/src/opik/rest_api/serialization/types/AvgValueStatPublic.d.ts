/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import * as core from "../../core";
export declare const AvgValueStatPublic: core.serialization.ObjectSchema<serializers.AvgValueStatPublic.Raw, OpikApi.AvgValueStatPublic>;
export declare namespace AvgValueStatPublic {
    interface Raw {
        value?: number | null;
    }
}
