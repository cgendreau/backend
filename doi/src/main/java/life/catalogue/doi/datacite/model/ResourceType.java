package life.catalogue.doi.datacite.model;

public enum ResourceType {

  AUDIOVISUAL("Audiovisual"),
  BOOK("Book"),
  BOOK_CHAPTER("BookChapter"),
  COLLECTION("Collection"),
  COMPUTATIONAL_NOTEBOOK("ComputationalNotebook"),
  CONFERENCE_PAPER("ConferencePaper"),
  CONFERENCE_PROCEEDING("ConferenceProceeding"),
  DATA_PAPER("DataPaper"),
  DATASET("Dataset"),
  DISSERTATION("Dissertation"),
  EVENT("Event"),
  IMAGE("Image"),
  INTERACTIVE_RESOURCE("InteractiveResource"),
  JOURNAL("Journal"),
  JOURNAL_ARTICLE("JournalArticle"),
  MODEL("Model"),
  OUTPUT_MANAGEMENT_PLAN("OutputManagementPlan"),
  PEER_REVIEW("PeerReview"),
  PHYSICAL_OBJECT("PhysicalObject"),
  PREPRINT("Preprint"),
  REPORT("Report"),
  SERVICE("Service"),
  SOFTWARE("Software"),
  SOUND("Sound"),
  STANDARD("Standard"),
  TEXT("Text"),
  WORKFLOW("Workflow"),
  OTHER("Other");

  private final String value;

  ResourceType(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static ResourceType fromValue(String v) {
    for (ResourceType c : ResourceType.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    throw new IllegalArgumentException(v);
  }

}
