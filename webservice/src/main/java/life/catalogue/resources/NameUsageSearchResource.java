package life.catalogue.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.*;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Set;

@Produces(MediaType.APPLICATION_JSON)
@Path("/nameusage")
public class NameUsageSearchResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchResource.class);

  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageSearchResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> search(@BeanParam NameUsageSearchRequest query,
      @Valid @BeanParam Page page,
      @Context UriInfo uri) throws InvalidQueryException {
    if (uri != null) {
      query.addFilters(uri.getQueryParameters());
    }
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@Valid SearchRequestBody req, @Context UriInfo uri) throws InvalidQueryException {
    return search(req.request, req.page, uri);
  }

  public static class SearchRequestBody {

    @Valid
    public final NameUsageSearchRequest request;
    @Valid
    public final Page page;

    @JsonCreator
    public SearchRequestBody(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filter,
        @JsonProperty("facet") Set<NameUsageSearchParameter> facet,
        @JsonProperty("content") Set<NameUsageSearchRequest.SearchContent> content,
        @JsonProperty("sortBy") NameUsageSearchRequest.SortBy sortBy,
        @JsonProperty("q") String q,
        @JsonProperty("highlight") @DefaultValue("false") boolean highlight,
        @JsonProperty("reverse") @DefaultValue("false") boolean reverse,
        @JsonProperty("fuzzy") @DefaultValue("false") boolean fuzzy,
        @JsonProperty("offset") @DefaultValue("0") int offset,
        @JsonProperty("limit") @DefaultValue("10") int limit,
        @JsonProperty("prefix") @DefaultValue("false") boolean prefix,
        @JsonProperty("minRank") Rank minRank,
        @JsonProperty("maxRank") Rank maxRank) {
      request = new NameUsageSearchRequest(filter, facet, content, sortBy, q, highlight, reverse, fuzzy, prefix, minRank, maxRank);
      page = new Page(offset, limit);
    }
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggest(@BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    return suggestService.suggest(query);
  }
}
