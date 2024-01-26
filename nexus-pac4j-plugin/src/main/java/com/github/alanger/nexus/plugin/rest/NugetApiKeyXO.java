package com.github.alanger.nexus.plugin.rest;

import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;

/**
 * Api key json object.
 */
public class NugetApiKeyXO {

    @ApiModelProperty("nugetApiKey")
    private String nugetApiKey;

    public NugetApiKeyXO() {}

    public NugetApiKeyXO(char[] nugetApiKey) {
        this.nugetApiKey = new String(Preconditions.checkNotNull(nugetApiKey));
    }

    public NugetApiKeyXO(String nugetApiKey) {
        this.nugetApiKey = Preconditions.checkNotNull(nugetApiKey);
    }

    public String getApiKey() {
        return this.nugetApiKey;
    }

    public void setApiKey(String nugetApiKey) {
        this.nugetApiKey = Preconditions.checkNotNull(nugetApiKey);
    }
}
