/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as core from "../../core";
import * as serializers from "../index";
import * as OpikApi from "../../api/index";
import { PercentageValueStatPublic } from "./PercentageValueStatPublic";
import { CountValueStatPublic } from "./CountValueStatPublic";
import { AvgValueStatPublic } from "./AvgValueStatPublic";
export declare const ProjectStatItemObjectPublic: core.serialization.Schema<serializers.ProjectStatItemObjectPublic.Raw, OpikApi.ProjectStatItemObjectPublic>;
export declare namespace ProjectStatItemObjectPublic {
    type Raw = ProjectStatItemObjectPublic.Percentage | ProjectStatItemObjectPublic.Count | ProjectStatItemObjectPublic.Avg;
    interface Percentage extends _Base, PercentageValueStatPublic.Raw {
        type: "PERCENTAGE";
    }
    interface Count extends _Base, CountValueStatPublic.Raw {
        type: "COUNT";
    }
    interface Avg extends _Base, AvgValueStatPublic.Raw {
        type: "AVG";
    }
    interface _Base {
        name?: string | null;
    }
}
