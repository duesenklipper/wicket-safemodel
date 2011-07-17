package de.duesenklipper.safemode;

import static de.duesenklipper.safemode.SafeModel.*;
import static org.junit.Assert.*;

import org.apache.wicket.model.IModel;
import org.junit.Test;

public class SafeModelTest {
    public static class Top {
        private Middle mid;

        public Middle getMid() {
            return mid;
        }

        public void setMid(Middle mid) {
            this.mid = mid;
        }
    }

    public static class Middle {
        private String string;

        public String getString() {
            return string;
        }

        public void setString(String string) {
            this.string = string;
        }
    }

    @Test
    public void simplePropertyModel() throws Exception {
        Middle mid = new Middle();
        mid.setString("testString");
        IModel<String> model = model(from(mid).getString());
        assertEquals("testString", model.getObject());
    }

    @Test
    public void multiStepPropertyModel() throws Exception {
        Top top = new Top();
        top.setMid(new Middle());
        top.getMid().setString("testString");
        IModel<String> model = model(from(top).getMid().getString());
        assertEquals("testString", model.getObject());
    }
}
