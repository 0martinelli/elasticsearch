/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.fetch;

import org.elasticsearch.TransportVersions;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.SearchPhaseResult;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.internal.ShardSearchContextId;
import org.elasticsearch.search.profile.ProfileResult;

import java.io.IOException;

public final class FetchSearchResult extends SearchPhaseResult {

    private SearchHits hits;
    // client side counter
    private transient int counter;

    private ProfileResult profileResult;

    public FetchSearchResult() {}

    public FetchSearchResult(ShardSearchContextId id, SearchShardTarget shardTarget) {
        this.contextId = id;
        setSearchShardTarget(shardTarget);
    }

    public FetchSearchResult(StreamInput in) throws IOException {
        super(in);
        contextId = new ShardSearchContextId(in);
        hits = new SearchHits(in);
        if (in.getTransportVersion().onOrAfter(TransportVersions.V_7_16_0)) {
            profileResult = in.readOptionalWriteable(ProfileResult::new);
        } else {
            profileResult = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        contextId.writeTo(out);
        hits.writeTo(out);
        if (out.getTransportVersion().onOrAfter(TransportVersions.V_7_16_0)) {
            out.writeOptionalWriteable(profileResult);
        }
    }

    @Override
    public FetchSearchResult fetchResult() {
        return this;
    }

    public void shardResult(SearchHits hits, ProfileResult profileResult) {
        assert assertNoSearchTarget(hits);
        this.hits = hits;
        assert this.profileResult == null;
        this.profileResult = profileResult;
    }

    private static boolean assertNoSearchTarget(SearchHits hits) {
        for (SearchHit hit : hits.getHits()) {
            assert hit.getShard() == null : "expected null but got: " + hit.getShard();
        }
        return true;
    }

    public SearchHits hits() {
        return hits;
    }

    public FetchSearchResult initCounter() {
        counter = 0;
        return this;
    }

    public int counterGetAndIncrement() {
        return counter++;
    }

    public ProfileResult profileResult() {
        return profileResult;
    }
}
