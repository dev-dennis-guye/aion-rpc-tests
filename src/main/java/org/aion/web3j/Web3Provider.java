package org.aion.web3j;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.aion.rpc.client.Provider;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.errors.RPCExceptions.InvalidRequestRPCException;
import org.aion.rpc.types.RPCTypes.RPCError;
import org.aion.rpc.types.RPCTypes.Request;
import org.aion.rpc.types.RPCTypes.Response;
import org.aion.rpc.types.RPCTypesConverter.RequestConverter;
import org.aion.rpc.types.RPCTypesConverter.ResponseConverter;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Web3Provider implements Provider {

    private static final Web3Provider WEB_3_PROVIDER = new Web3Provider();
    private static final Logger logger = LoggerFactory.getLogger(Web3Provider.class);
    private final ExecutorService threadPool;
    private CloseableHttpClient httpclient;
    private String provider = "http://localhost:8545";

    private Web3Provider() {
        threadPool = Executors.newCachedThreadPool();
        httpclient = HttpClients.createDefault();
        httpclient = HttpClientBuilder.create()
            .setConnectionManager(new PoolingHttpClientConnectionManager()).build();
    }

    public static Web3Provider getInstance() {
        return WEB_3_PROVIDER;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     *
     * @param request the request to be executed
     * @param resultConverter the decoder to be used on the result
     * @param asyncTask the task to be executed upon completion of the request
     * @param <R> the result of the rpc request
     * @param <O> the output of the function
     * @return the future result of the call
     */
    @Override
    public <R, O> CompletableFuture<O> executeAsync(Request request,
        Function<Object, R> resultConverter,
        BiFunction<R, RPCError, O> asyncTask) {
        Objects.requireNonNull(request);
        return CompletableFuture
            .supplyAsync(() -> packageRequest(request, asyncTask, resultConverter), threadPool);
    }

    /**
     *
     * @param request the rpc request to be executed
     * @param resultConverter the decoder to be used on the result
     * @param <R> the result of the rpc request
     * @return the result of the request
     * @throws org.aion.rpc.errors.RPCExceptions.RPCException if the rpc request was not successfully executed
     * @throws HttpException if the response was not 200
     */
    @SuppressWarnings("ConstantConditions")
    public <R> R execute(Request request, Function<Object, R> resultConverter) {
        Response response;
        try {
            String jsonPayload = RequestConverter.encodeStr(request);
            logger.trace("Executing request: {}", jsonPayload);
            HttpPost post = buildHttpPost(provider, jsonPayload);
            CloseableHttpResponse httpResponse = httpclient.execute(post);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                logger.trace("Request Succeeded!");
                HttpEntity responseEntity = httpResponse.getEntity();
                InputStream inputStream = responseEntity.getContent();
                int i;
                StringBuilder responseString = new StringBuilder();
                while ((i = inputStream.read()) != -1) {
                    responseString.append((char) i);
                }
                if (responseString.length() == 0) {
                    return null;
                } else {
                    response = ResponseConverter.decode(responseString.toString());
                    if (response.error != null) {
                        throw RPCExceptions.fromCode(response.error.code);
                    } else {
                        return resultConverter.apply(response.result);
                    }
                }
            } else {
                logger.warn("Http request failed with: {}",
                    httpResponse.getStatusLine().getStatusCode());
                throw new HttpException(httpResponse.getStatusLine());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed with exception ", e);
            throw InvalidRequestRPCException.INSTANCE;
        }
    }

    /**
     * Builds the HTTP post to be executed
     * @param provider the web3 socket address
     * @param jsonPayload the request to be executed
     * @return an http post that can be executed by an Http client
     * @throws UnsupportedEncodingException if the json payload is not encoded correctly
     */
    private HttpPost buildHttpPost(String provider, String jsonPayload)
        throws UnsupportedEncodingException {
        HttpPost post = new HttpPost(provider);
        post.setHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
        post.setHeader("Content-type", ContentType.APPLICATION_JSON.getMimeType());
        post.setEntity(new StringEntity(jsonPayload));
        return post;
    }

    private <R, O> O packageRequest(Request request, BiFunction<R, RPCError, O> asyncTask,
        Function<Object, R> resultConverter) {
        RPCError error = null;
        Response response;
        try {
            String jsonPayload = RequestConverter.encodeStr(request);
            logger.trace("Executing request: {}", jsonPayload);
            HttpPost post = buildHttpPost(provider, jsonPayload);
            CloseableHttpResponse httpResponse = httpclient.execute(post);
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                HttpEntity responseEntity = httpResponse.getEntity();
                InputStream inputStream = responseEntity.getContent();
                int i;
                StringBuilder responseString = new StringBuilder();
                while ((i = inputStream.read()) != -1) {
                    responseString.append((char) i);
                }
                if (responseString.length() == 0) {
                    return asyncTask.apply(null, null);
                } else {
                    response = ResponseConverter.decode(responseString.toString());
                    //noinspection ConstantConditions
                    return asyncTask
                        .apply(resultConverter.apply(response.result), response.error);
                }
            }
        } catch (Exception e) {
            error = InvalidRequestRPCException.INSTANCE.getError();
        }
        return asyncTask.apply(null, error);
    }
}
