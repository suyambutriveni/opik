/**
 * This file was auto-generated by Fern from our API Definition.
 */
import * as environments from "../../../../environments";
import * as core from "../../../../core";
import * as OpikApi from "../../../index";
export declare namespace Check {
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
 * Access check resources
 */
export declare class Check {
    protected readonly _options: Check.Options;
    constructor(_options?: Check.Options);
    /**
     * Check user access to workspace
     *
     * @param {OpikApi.AuthDetailsHolder} request
     * @param {Check.RequestOptions} requestOptions - Request-specific configuration.
     *
     * @throws {@link OpikApi.UnauthorizedError}
     * @throws {@link OpikApi.ForbiddenError}
     *
     * @example
     *     await client.check.access({
     *         "key": "value"
     *     })
     */
    access(request: OpikApi.AuthDetailsHolder, requestOptions?: Check.RequestOptions): core.APIPromise<void>;
}
