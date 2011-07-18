package de.wicketbuch.safemode;

import static de.wicketbuch.safemode.SafeModel.from;
import static de.wicketbuch.safemode.SafeModel.model;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
}
