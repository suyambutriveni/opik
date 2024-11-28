"use strict";
/**
 * This file was auto-generated by Fern from our API Definition.
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatasetItemPublic = void 0;
const core = __importStar(require("../../core"));
const JsonNodePublic_1 = require("./JsonNodePublic");
const DatasetItemPublicSource_1 = require("./DatasetItemPublicSource");
const JsonNode_1 = require("./JsonNode");
const ExperimentItemPublic_1 = require("./ExperimentItemPublic");
exports.DatasetItemPublic = core.serialization.object({
    id: core.serialization.string().optional(),
    input: JsonNodePublic_1.JsonNodePublic.optional(),
    expectedOutput: core.serialization.property("expected_output", JsonNodePublic_1.JsonNodePublic.optional()),
    metadata: JsonNodePublic_1.JsonNodePublic.optional(),
    traceId: core.serialization.property("trace_id", core.serialization.string().optional()),
    spanId: core.serialization.property("span_id", core.serialization.string().optional()),
    source: DatasetItemPublicSource_1.DatasetItemPublicSource,
    data: JsonNode_1.JsonNode.optional(),
    experimentItems: core.serialization.property("experiment_items", core.serialization.list(ExperimentItemPublic_1.ExperimentItemPublic).optional()),
    createdAt: core.serialization.property("created_at", core.serialization.date().optional()),
    lastUpdatedAt: core.serialization.property("last_updated_at", core.serialization.date().optional()),
    createdBy: core.serialization.property("created_by", core.serialization.string().optional()),
    lastUpdatedBy: core.serialization.property("last_updated_by", core.serialization.string().optional()),
});