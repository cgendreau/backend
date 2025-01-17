package life.catalogue.dw.jersey.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

/**
 * Dataset JSON body reader that understands the difference between an explicit property given with a nil value and a missing property.
 * Missing properties will be read as true java NULL values, while the explicit JS nil will be converted into the type specific patch null value.
 * See Dataset#applyPatch method.
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class DatasetPatchMessageBodyRW implements MessageBodyReader<Dataset>, MessageBodyWriter<Dataset> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetPatchMessageBodyRW.class);
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return type == Dataset.class && Arrays.stream(annotations).anyMatch(a -> a.annotationType().equals(DatasetPatch.class));
  }

  @Override
  public Dataset readFrom(Class<Dataset> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    Map<String, Object> map = ApiModule.MAPPER.readValue(entityStream, MAP_TYPE);
    for (Map.Entry<String, Object> field : map.entrySet()) {
      if (field.getValue() == null && Dataset.NULL_TYPES.containsKey(field.getKey())) {
        field.setValue(Dataset.NULL_TYPES.get(field.getKey()));
      }
    }
    Dataset ad = ApiModule.MAPPER.convertValue(map, Dataset.class);
    return ad;
  }

  @Override
  public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return isReadable(aClass, type, annotations, mediaType);
  }

  @Override
  public void writeTo(Dataset dataset, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
    for (PropertyDescriptor p : Dataset.PATCH_PROPS) {
      try {
        if (Dataset.NULL_TYPES.containsKey(p.getName())) {
          Object nullType = Dataset.NULL_TYPES.get(p.getName());
          if (nullType.equals(p.getReadMethod().invoke(dataset))) {
              p.getWriteMethod().invoke(dataset, (Object) null);
          }
        }
      } catch (Exception e) {
        LOG.error("Fail to set dataset patch field {} to null", p.getName(), e);
      }
    }
    ApiModule.MAPPER.writeValue(outputStream, dataset);
  }
}