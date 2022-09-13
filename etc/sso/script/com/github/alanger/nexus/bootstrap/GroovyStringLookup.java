package com.github.alanger.nexus.bootstrap;

import java.util.Objects;

import javax.script.ScriptEngine;

import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.configuration2.interpol.Lookup;

import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

public class GroovyStringLookup implements StringLookup, Lookup {

    public static final GroovyStringLookup INSTANCE = new GroovyStringLookup();

    private GroovyStringLookup() {
    }

    @Override
    public String lookup(final String script) {
        if (script == null) {
            return null;
        }
        try {
            final ScriptEngine scriptEngine = new GroovyScriptEngineImpl();
            return Objects.toString(scriptEngine.eval(script), null);
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    String.format("Error in Groovy script engine evaluating script [%s].", script), e);
        }
    }

}
