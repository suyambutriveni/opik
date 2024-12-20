/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as environments from "../../../../environments";
import * as core from "../../../../core";
import * as OpikApi from "../../../index";
export declare namespace SystemUsage {
    interface Options {
        environment?: core.Supplier<environments.OpikApiEnvironment | string>;
    }
    interface RequestOptions {
        /** The maximum time to wait for a response in seconds. */
        timeoutInSeconds?: number;
        /** The number of times to retry the request. Defaults to 2. */
        maxRetries?: number;
        /** A hook to abort the request. */
        abortSignal?: AbortSignal;
        /** Additional headers to include in the request. */
        headers?: Record<string, string>;
    }
}
/**
 * System usage related resource
 */
export declare class SystemUsage {
    protected readonly _options: SystemUsage.Options;
    constructor(_options?: SystemUsage.Options);
    /**
     * Get datasets information for BI events per user per workspace
     *
     * @param {SystemUsage.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.systemUsage.getDatasetBiInfo()
     */
    getDatasetBiInfo(requestOptions?: SystemUsage.RequestOptions): core.APIPromise<OpikApi.BiInformationResponse>;
    /**
     * Get experiments information for BI events per user per workspace
     *
     * @param {SystemUsage.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.systemUsage.getExperimentBiInfo()
     */
    getExperimentBiInfo(requestOptions?: SystemUsage.RequestOptions): core.APIPromise<OpikApi.BiInformationResponse>;
    /**
     * Get traces information for BI events per user per workspace
     *
     * @param {SystemUsage.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.systemUsage.getTracesBiInfo()
     */
    getTracesBiInfo(requestOptions?: SystemUsage.RequestOptions): core.APIPromise<OpikApi.BiInformationResponse>;
    /**
     * Get traces count on previous day for all available workspaces
     *
     * @param {SystemUsage.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @example
     *     await client.systemUsage.getTracesCountForWorkspaces()
     */
    getTracesCountForWorkspaces(requestOptions?: SystemUsage.RequestOptions): core.APIPromise<OpikApi.TraceCountResponse>;
}
