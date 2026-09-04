---
name: Custom JVM argument sanitization
description: Durable rule for preventing malformed user JVM text from becoming the Java main class.
---

User-provided JVM argument text is inserted before generated Minecraft arguments, so any orphan token that does not begin with `-` can be interpreted by Java as the main class and cause a misleading `ClassNotFoundException`.

**Why:** A launch log showed a stray one-character custom argument being passed directly to Java, which prevented Minecraft from starting before renderer or game initialization.

**How to apply:** Sanitize custom JVM tokens only at the shared Java-launch boundary. Preserve flags and options with separate values such as classpath/module-path options, discard orphan non-option tokens, and log each discarded value. Do not filter generated Minecraft arguments.