package de.duesenklipper.safemode;

import static de.duesenklipper.safemode.SafeModel.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.model.IModel;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SafeModelTest {

    private WicketTester tester;

    @Before
    public void setUp() {
        this.tester = new WicketTester();
    }

    @After
    public void tearDown() {
        this.tester.destroy();
    }

    public static class Top {
        private Middle mid;
        private List<Middle> mids = new ArrayList<Middle>();

        public Middle getMid() {
            return mid;
        }

        public void setMid(Middle mid) {
            this.mid = mid;
        }

        public List<Middle> getMids() {
            return mids;
        }

        public void setMids(List<Middle> mids) {
            this.mids = mids;
        }
    }

    public static class Middle {
        private String string;
        private Bottom bot;

        public String getString() {
            return string;
        }

        public void setString(String string) {
            this.string = string;
        }

        public Bottom getBot() {
            return bot;
        }

        public void setBot(Bottom bot) {
            this.bot = bot;
        }
    }

    public static class Bottom {
        private int value = 0;

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
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

    @Test
    public void customBeanPropertyModel() throws Exception {
        Top top = new Top();
        top.setMid(new Middle());
        final Bottom bot = new Bottom();
        bot.setValue(42);
        top.getMid().setBot(bot);
        IModel<Bottom> model = model(from(top).getMid().getBot());
        assertSame(bot, model.getObject());
    }

    @Test
    public void nullLeafModel() throws Exception {
        Top top = new Top();
        IModel<Middle> model = model(from(top).getMid());
        assertNull(model.getObject());
        Middle mid = new Middle();
        model.setObject(mid);
        assertSame(mid, top.getMid());
    }

    @Test
    public void nullInTreeModel() throws Exception {
        Top top = new Top();
        IModel<Bottom> model = model(from(top).getMid().getBot());
        assertNull(model.getObject());
    }

    @Test
    public void listModel() throws Exception {
        Top top = new Top();
        Middle mid = new Middle();
        top.getMids().add(mid);
        IModel<Middle> model = model(from(top).getMids().get(0));
        assertSame(mid, model.getObject());
        Middle newMid = new Middle();
        model.setObject(newMid);
        assertNotSame(mid, model.getObject());
        assertSame(newMid, model.getObject());
    }
}
