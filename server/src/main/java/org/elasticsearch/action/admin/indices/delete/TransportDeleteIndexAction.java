/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.delete;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.DestructiveOperations;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.AcknowledgedTransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetadataDeleteIndexService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.Index;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Delete index action.
 */
public class TransportDeleteIndexAction extends AcknowledgedTransportMasterNodeAction<DeleteIndexRequest> {

    private static final Logger logger = LogManager.getLogger(TransportDeleteIndexAction.class);

    private final MetadataDeleteIndexService deleteIndexService;
    private final DestructiveOperations destructiveOperations;

    @Inject
    public TransportDeleteIndexAction(TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                      MetadataDeleteIndexService deleteIndexService, ActionFilters actionFilters,
                                      IndexNameExpressionResolver indexNameExpressionResolver,
                                      DestructiveOperations destructiveOperations) {
        super(DeleteIndexAction.NAME, transportService, clusterService, threadPool, actionFilters, DeleteIndexRequest::new,
            indexNameExpressionResolver, ThreadPool.Names.SAME);
        this.deleteIndexService = deleteIndexService;
        this.destructiveOperations = destructiveOperations;
    }

    @Override
    protected void doExecute(Task task, DeleteIndexRequest request, ActionListener<AcknowledgedResponse> listener) {
        destructiveOperations.failDestructive(request.indices());
        super.doExecute(task, request, listener);
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteIndexRequest request, ClusterState state) {
        return state.blocks().indicesAllowReleaseResources(indexNameExpressionResolver.concreteIndexNames(state, request));
    }

    @Override
    protected void masterOperation(Task task, final DeleteIndexRequest request, final ClusterState state,
                                   final ActionListener<AcknowledgedResponse> listener) {
        final Set<Index> concreteIndices = new HashSet<>(Arrays.asList(indexNameExpressionResolver.concreteIndices(state, request)));
        if (concreteIndices.isEmpty()) {
            listener.onResponse(AcknowledgedResponse.TRUE);
            return;
        }

        DeleteIndexClusterStateUpdateRequest deleteRequest = new DeleteIndexClusterStateUpdateRequest()
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout())
            .indices(concreteIndices.toArray(new Index[concreteIndices.size()]));

        deleteIndexService.deleteIndices(deleteRequest, new ActionListener<>() {

            @Override
            public void onResponse(AcknowledgedResponse response) {
                listener.onResponse(response);
            }

            @Override
            public void onFailure(Exception t) {
                logger.debug(() -> new ParameterizedMessage("failed to delete indices [{}]", concreteIndices), t);
                listener.onFailure(t);
            }
        });
    }
}
