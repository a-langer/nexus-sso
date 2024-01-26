package com.github.alanger.nexus.plugin;

import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;

/**
 * Init message json object.
 */
public class InitResourceXO {

    @ApiModelProperty("message")
    private String message;

    public InitResourceXO() {}

    public InitResourceXO(String message) {
        this.message = Preconditions.checkNotNull(message);
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = Preconditions.checkNotNull(message);
    }

}
