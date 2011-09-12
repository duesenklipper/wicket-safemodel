/**
 * Copyright (C) 2011 Carl-Eric Menzel <cmenzel@wicketbuch.de>
 * and possibly other SafeModel contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.wicketbuch.safemodel;

import static de.wicketbuch.safemodel.SafeModel.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.tester.WicketTester;
import org.jmock.api.Invocation;
import org.jmock.api.Invokable;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
        private Map<String, Bottom> bottomMap = new HashMap<String, Bottom>();

        public Map<String, Bottom> getBottomMap() {
            return bottomMap;
        }

        public void setBottomMap(final Map<String, Bottom> bottomMap) {
            this.bottomMap = bottomMap;
        }

        public Middle getMid() {
            return mid;
        }

        public void setMid(final Middle mid) {
            this.mid = mid;
        }

        public List<Middle> getMids() {
            return mids;
        }

        public void setMids(final List<Middle> mids) {
            this.mids = mids;
        }
    }

    public static class Middle {
        private String string;
        private Bottom bot;

        public String getString() {
            return string;
        }

        public void setString(final String string) {
            this.string = string;
        }

        public Bottom getBot() {
            return bot;
        }

        public void setBot(final Bottom bot) {
            this.bot = bot;
        }
    }

    public static class Bottom {
        private int value = 0;

        public int getValue() {
            return value;
        }

        public void setValue(final int value) {
            this.value = value;
        }
    }

    @Test
    public void simplePropertyModel() throws Exception {
        final Middle mid = new Middle();
        mid.setString("testString");
        final IModel<String> model = model(from(mid).getString());
        assertEquals("testString", model.getObject());
        model.setObject("newString");
        assertEquals("newString", mid.getString());
    }

    @Test
    public void multiStepPropertyModel() throws Exception {
        final Top top = new Top();
        top.setMid(new Middle());
        top.getMid().setString("testString");
        final IModel<String> model = model(from(top).getMid().getString());
        assertEquals("testString", model.getObject());
    }

    @Test
    public void customBeanPropertyModel() throws Exception {
        final Top top = new Top();
        top.setMid(new Middle());
        final Bottom bot = new Bottom();
        bot.setValue(42);
        top.getMid().setBot(bot);
        final IModel<Bottom> model = model(from(top).getMid().getBot());
        assertSame(bot, model.getObject());
    }

    @Test
    public void nullLeafModel() throws Exception {
        final Top top = new Top();
        final IModel<Middle> model = model(from(top).getMid());
        assertNull(model.getObject());
        final Middle mid = new Middle();
        model.setObject(mid);
        assertSame(mid, top.getMid());
    }

    @Test
    public void nullInTreeModel() throws Exception {
        final Top top = new Top();
        final IModel<Bottom> model = model(from(top).getMid().getBot());
        assertNull(model.getObject());
    }

    @Test
    public void listAsLeaf() throws Exception {
        final Top top = new Top();
        final Middle mid = new Middle();
        top.getMids().add(mid);
        final IModel<Middle> model = model(from(top).getMids().get(0));
        assertSame(mid, model.getObject());
        final Middle newMid = new Middle();
        model.setObject(newMid);
        assertNotSame(mid, model.getObject());
        assertSame(newMid, model.getObject());
        assertSame(newMid, top.getMids().get(0));
        assertEquals(1, top.getMids().size());
    }

    @Test
    public void listInPath() throws Exception {
        final Top top = new Top();
        final Middle mid = new Middle();
        top.getMids().add(mid);
        final IModel<Bottom> model = model(from(top).getMids().get(0).getBot());
        assertNull(model.getObject());
        final Bottom bot = new Bottom();
        model.setObject(bot);
        assertSame(bot, model.getObject());
        assertSame(bot, top.getMids().get(0).getBot());
    }

    @Test
    public void mapAsLeaf() throws Exception {
        final Top top = new Top();
        final Bottom bot1 = new Bottom();
        top.getBottomMap().put("bot1", bot1);
        final Bottom bot2 = new Bottom();
        top.getBottomMap().put("bot2", bot2);
        final IModel<Bottom> model = model(from(top).getBottomMap().get("bot1"));
        assertEquals(bot1, model.getObject());
        model.setObject(bot2);
        assertEquals(bot2, top.getBottomMap().get("bot1")); // should point to
                                                            // bot2 now
    }

    @Test
    public void mapInPath() throws Exception {
        final Top top = new Top();
        final Bottom bot1 = new Bottom();
        top.getBottomMap().put("bot1", bot1);
        bot1.setValue(42);
        final Bottom bot2 = new Bottom();
        top.getBottomMap().put("bot2", bot2);
        final IModel<Integer> model = model(from(top).getBottomMap().get("bot1").getValue());
        assertEquals(Integer.valueOf(42), model.getObject());
        model.setObject(43);
        assertEquals(43, bot1.getValue());
    }

    public static interface MidService {
        Middle loadMid(int id);

        static class NotFoundException extends RuntimeException {

        }
    }

    private boolean calledLoadMid = false;
    final Middle mid = new Middle();
    {
        final Bottom bot = new Bottom();
        bot.setValue(42);
        mid.setBot(bot);
    }

    public class MidServiceImpl implements MidService {

        public Middle loadMid(final int id) {
            calledLoadMid = true;
            if (id == 42) {
                return mid;
            } else {
                throw new NotFoundException();
            }
        }
    }

    @Test
    public void loadFromService() throws Exception {
        final MidService service = new MidServiceImpl();
        final IModel<Middle> model = model(fromService(service).loadMid(42));
        assertSame(mid, model.getObject());
        assertTrue(calledLoadMid);
        calledLoadMid = false;
        assertSame(mid, model.getObject());
        assertFalse(calledLoadMid);
    }

    @Test
    public void modelAsTarget() throws Exception {
        final Top top = new Top();
        final Middle mid = new Middle();
        top.setMid(mid);
        final IModel<Top> rootModel = new AbstractReadOnlyModel<SafeModelTest.Top>() {

            @Override
            public Top getObject() {
                return top;
            }
        };
        final IModel<Middle> model = model(from(rootModel).getMid());
        assertSame(mid, model.getObject());
    }

    @Test
    @Ignore("does not work due to bug in PropertyModel in wicket1.4.17, waiting for fix")
    public void listModelAsTarget() throws Exception {
        final Top top = new Top();
        final Middle mid = new Middle();
        top.setMid(mid);
        final List<Top> list = new ArrayList<Top>();
        list.add(top);
        final IModel<List<Top>> rootModel = new AbstractReadOnlyModel<List<Top>>() {

            @Override
            public List<Top> getObject() {
                return list;
            }
        };
        final IModel<Middle> model = model(from(rootModel).get(0).getMid());
        assertSame(mid, model.getObject());
    }

    @Test
    public void nestedServiceModel() throws Exception {
        final MidService service = new MidServiceImpl();
        final IModel<Integer> model = model(from(model(fromService(service).loadMid(42))).getBot().getValue());
        assertEquals(Integer.valueOf(42), model.getObject());
    }

    @Test
    public void proxiedService() throws Exception {
        final Middle expected = new Middle();
        MidService mockedService = ClassImposteriser.INSTANCE.imposterise(new Invokable() {

            public Object invoke(Invocation invocation) throws Throwable {
                return expected;
            }
        }, MidServiceImpl.class);
        IModel<Middle> model = model(fromService(mockedService).loadMid(42));
        assertSame(expected, model.getObject());
    }
}
