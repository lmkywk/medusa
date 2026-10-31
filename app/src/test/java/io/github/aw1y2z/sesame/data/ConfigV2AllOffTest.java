package io.github.aw1y2z.sesame.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;

/** 公开包新装默认：任务/域布尔开关全关，数值类保持预设值。 */
public class ConfigV2AllOffTest {

    @Test
    public void allOffTurnsEveryBooleanOffButKeepsNumbers() {
        ConfigV2 config = new ConfigV2();
        ModelFields fields = new ModelFields();
        fields.addField(new BooleanModelField("enable", "启用", true));
        fields.addField(new BooleanModelField("waterFriend", "浇水", true));
        fields.addField(new IntegerModelField("count", "次数", 10));
        config.getModelFieldsMap().put("TestModel", fields);

        ConfigV2.applyAllOffBooleans(config);

        assertFalse((Boolean) fields.get("enable").getValue());
        assertFalse((Boolean) fields.get("waterFriend").getValue());
        assertEquals(10, fields.get("count").getValue());
    }
}
