package life.catalogue.db.mapper;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.type2.StringCount;

import org.gbif.dwc.terms.Term;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

/**
 * The MyBatis mapper interface for DatasetImport.
 */
public interface DatasetImportMapper extends DatasetProcessable<DatasetImport> {
  
  DatasetImport get(@Param("key") int datasetKey, @Param("attempt") int attempt);

  /**
   * @param datasetKey
   * @return Return last import attempt for given dataset or null
   */
  DatasetImport last(@Param("key") int datasetKey);

  /**
   * Count all imports by their state
   */
  int count(@Param("key") @Nullable Integer datasetKey, @Param("states") Collection<ImportState> states);
  
  /**
   * List all imports optionally filtered by their datasetKey and state(s).
   * Ordered by starting date from latest to historical.
   */
  List<DatasetImport> list(@Param("key") @Nullable Integer datasetKey,
                           @Param("states") @Nullable Collection<ImportState> states,
                           @Param("page") Page page);

  /**
   * Creates a new dataset import and sets the new attempt in the DatasetImport object
   */
  void create(@Param("imp") DatasetImport datasetImport);
  
  void update(@Param("imp") DatasetImport datasetImport);

  Integer countBareName(@Param("key") int datasetKey);
  Integer countDistribution(@Param("key") int datasetKey);
  Integer countEstimate(@Param("key") int datasetKey);
  Integer countMedia(@Param("key") int datasetKey);
  Integer countName(@Param("key") int datasetKey);
  Integer countReference(@Param("key") int datasetKey);
  Integer countSynonym(@Param("key") int datasetKey);
  Integer countTaxon(@Param("key") int datasetKey);
  Integer countTreatment(@Param("key") int datasetKey);
  Integer countTypeMaterial(@Param("key") int datasetKey);
  Integer countVerbatim(@Param("key") int datasetKey);
  Integer countVernacular(@Param("key") int datasetKey);
  
  List<StringCount> countDistributionsByGazetteer(@Param("key") int datasetKey);
  List<StringCount> countExtinctTaxaByRank(@Param("key") int datasetKey);
  List<StringCount> countIssues(@Param("key") int datasetKey);
  List<StringCount> countMediaByType(@Param("key") int datasetKey);
  List<StringCount> countNameRelationsByType(@Param("key") int datasetKey);
  List<StringCount> countNamesByCode(@Param("key") int datasetKey);
  List<StringCount> countNamesByRank(@Param("key") int datasetKey);
  List<StringCount> countNamesByStatus(@Param("key") int datasetKey);
  List<StringCount> countNamesByType(@Param("key") int datasetKey);
  List<StringCount> countSpeciesInteractionsByType(@Param("key") int datasetKey);
  List<StringCount> countSynonymsByRank(@Param("key") int datasetKey);
  List<StringCount> countTaxaByRank(@Param("key") int datasetKey);
  List<StringCount> countTaxonConceptRelationsByType(@Param("key") int datasetKey);
  List<StringCount> countTypeMaterialByStatus(@Param("key") int datasetKey);
  List<StringCount> countUsagesByOrigin(@Param("key") int datasetKey);
  List<StringCount> countUsagesByStatus(@Param("key") int datasetKey);
  List<StringCount> countVerbatimByType(@Param("key") int datasetKey);
  List<StringCount> countVerbatimTerms(@Param("key") int datasetKey, @Param("rowType") Term rowType);
  List<StringCount> countVernacularsByLanguage(@Param("key") int datasetKey);


}
