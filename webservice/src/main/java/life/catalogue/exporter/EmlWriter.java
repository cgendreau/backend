package life.catalogue.exporter;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import life.catalogue.api.model.Dataset;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public class EmlWriter  {

  private EmlWriter() {
  }

  public static void write(Dataset d, File f) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      write(d,w);
    }
  }

  public static void write(Dataset d, OutputStream out) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      write(d,w);
    }
  }

  public static void write(Dataset d, Writer w) throws IOException {
    try {
      Template temp = FmUtil.FMK.getTemplate("/dwca/eml.ftl");
      temp.process(d, w);
    } catch (TemplateException e) {
      throw new IOException(e);
    }
  }

}
