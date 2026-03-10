/**
 *
 */
package org.javarosa.engine.models;

import org.javarosa.core.model.instance.FormInstance;

import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * @author ctsims
 *
 */
public class Mockup {
    final HashMap<String, FormInstance> instances;
    Date date;
    final ArrayList<Session> sessions;

    public Mockup() {
        sessions = new ArrayList<>();
        instances = new HashMap<>();
    }

    public HashMap<String, FormInstance> getInstances() {
        return instances;
    }

    public Date getDate() {
        return date;
    }

    public MockupEditor getEditor() {
        return new MockupEditor(this);
    }

    public class MockupEditor {
        final Mockup m;
        private MockupEditor(Mockup m) {
            this.m = m;
        }

        public void commit() {

        }

        public void setDate(Date d) {
            m.date = d;
        }

        public void addInstance(FormInstance instance) {
            m.instances.put(instance.getInstanceId(), instance);
        }

        public void addSession(Session s) {
            m.sessions.add(s);
        }
    }
}
