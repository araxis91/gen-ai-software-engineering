# Research Notes — context7 Queries (Agent 2 / Task 2 & Task 4)

Queries made via the `context7` MCP server while building the Java multi-agent pipeline.

## Query 1: Jackson databind — BigDecimal + OffsetDateTime (java.time) handling

- **Search**: "Deserialize BigDecimal from a quoted JSON string and register JavaTimeModule for OffsetDateTime with ObjectMapper"
- **context7 library ID**: `/fasterxml/jackson-databind`
- **Key insight applied**: classic `jackson-databind` (2.x) `ObjectMapper` does **not** auto-register `java.time` support the way the newer 3.x `tools.jackson` `MapperBuilder` does (that auto-registration is a 3.x-only behavior via `JavaTimeInitializer`). For our Jackson 2.x setup, `JavaTimeModule` must be registered explicitly and `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` disabled so `OffsetDateTime` fields serialize as ISO-8601 strings instead of numeric epoch arrays. Applied in `JsonMapper.newObjectMapper()`:
  ```java
  ObjectMapper mapper = new ObjectMapper();
  mapper.registerModule(new JavaTimeModule());
  mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  ```
  Also confirmed Jackson's built-in `BigDecimal` deserializer accepts a quoted JSON string token (not just a bare numeric token), which matches `sample-transactions.json`'s `"amount": "1500.00"` format — no custom deserializer needed for `Transaction.amount`.

## Query 2: JaCoCo Maven plugin — coverage threshold enforcement

- **Search**: "maven-plugin check goal rule to fail build when line coverage is below a minimum percentage"
- **context7 library ID**: `/websites/jacoco_jacoco_trunk_doc`
- **Key insight applied**: the `jacoco:check` goal (bound to the `verify` lifecycle phase) takes a `<rules>` block with `<element>BUNDLE</element>`, a `<counter>LINE</counter>` / `<value>COVEREDRATIO</value>` limit, and a `<minimum>` ratio (accepts `0.80` or `80%`); `haltOnFailure` (default `true`) fails the build when the threshold isn't met. Applied directly in `pom.xml`'s `jacoco-maven-plugin` execution so `mvn verify` fails locally under 80% line coverage — this is also what the Task 3 pre-push hook shells out to, so the hook and `mvn verify` agree on the same threshold instead of re-implementing the check twice.
  ```xml
  <rule>
    <element>BUNDLE</element>
    <limits>
      <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum>
      </limit>
    </limits>
  </rule>
  ```

## Query 3: FastMCP — static-URI resource returning plain text (Task 4 custom server)

- **Search**: "Defining a resource with a static URI (no template parameters) using the @mcp.resource decorator, and returning plain text"
- **context7 library ID**: `/prefecthq/fastmcp`
- **Key insight applied**: confirmed the idiomatic pattern for a non-templated resource is `@mcp.resource("scheme://path")` on a function returning a plain `str` (FastMCP wraps it in `ResourceContent` automatically — no need to construct a `ResourceResult` manually for the simple text case). Applied directly in `mcp/server.py`'s `pipeline_summary_resource()`, which registers `pipeline://summary` and returns the raw contents of `shared/results/pipeline-summary.json` (or a "no run yet" message) as text, matching the doc's `data://config` example shape but reading from disk instead of an in-memory dict.
  ```python
  @mcp.resource("pipeline://summary")
  def pipeline_summary_resource() -> str:
      return SUMMARY_FILE.read_text(encoding="utf-8")
  ```
