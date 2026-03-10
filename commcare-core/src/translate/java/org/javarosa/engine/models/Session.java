package org.javarosa.engine.models;

import org.javarosa.core.util.PropertyUtils;

import java.util.ArrayList;


/**
 * @author ctsims
 *
 */
public class Session {

    final String uuid;
    final ArrayList<Step> steps;

    public Session() {
        uuid = PropertyUtils.genUUID();
        steps = new ArrayList();
    }

    public ArrayList<Step> getSteps() {
        return steps;
    }

    public void addStep(Step step) {
        steps.add(step);
    }
}
