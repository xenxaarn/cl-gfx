# Vendored libraries

These jars are the official Shizuku API 13.1.5 artifacts, extracted from the
corresponding AARs (`classes.jar`), plus the androidx annotation jar that Shizuku's
public signatures reference. They are committed because this project has no Gradle
dependency resolution; `build.sh` feeds the jars directly to `javac` and `d8`.

| File | Coordinates | License |
|------|-------------|---------|
| `shizuku-api-13.1.5.jar` | `dev.rikka.shizuku:api:13.1.5` | MIT |
| `shizuku-provider-13.1.5.jar` | `dev.rikka.shizuku:provider:13.1.5` | MIT |
| `shizuku-shared-13.1.5.jar` | `dev.rikka.shizuku:shared:13.1.5` | MIT |
| `shizuku-aidl-13.1.5.jar` | `dev.rikka.shizuku:aidl:13.1.5` | MIT |
| `androidx-annotation-1.3.0.jar` | `androidx.annotation:annotation:1.3.0` | Apache-2.0 |

Source: <https://github.com/RikkaApps/Shizuku-API> and
<https://maven.google.com/web/index.html#androidx.annotation:annotation:1.3.0>.

To refresh, download the four AARs from Maven Central, extract each `classes.jar`,
and rename it to the matching file above. Do not modify the classes.
