package io.groundhog.jmeter;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.FileEditor;

import java.beans.PropertyDescriptor;

/**
 * @author Danny Thomas
 * @since 0.1
 */
public class HARSamplerBeanInfo extends BeanInfoSupport {

  public HARSamplerBeanInfo() {
    super(HARSampler.class);
    createPropertyGroup("configuration", new String[]{"filename"});
    PropertyDescriptor p = property("filename");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, "");
    p.setPropertyEditorClass(FileEditor.class);
  }

}
