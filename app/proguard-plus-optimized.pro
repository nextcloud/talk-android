# SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Optimize Compose rendering while retaining the existing reflection, JNI and serialization surface.
# Broad shrinking/renaming requires separate end-to-end validation of accounts, push and calls.
-dontshrink
-dontobfuscate
-keepattributes *
-keep class !androidx.compose.**,!com.nextcloud.talk.ui.chat.**,!com.nextcloud.talk.conversationlist.ui.**,** { *; }

# These absent types are referenced by retained third-party code, outside the optimization scope.
# Keep the exceptions exact so new missing dependencies still fail the build.
# Tink's optional HTTP key downloader, legacy GeoGson formatting and optional Firebase analytics.
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn com.google.common.base.Objects$ToStringHelper
-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector
# Desktop-only JTS drawing and Commons Lang method-handle adapter.
-dontwarn java.awt.Font
-dontwarn java.awt.Rectangle
-dontwarn java.awt.Shape
-dontwarn java.awt.font.FontRenderContext
-dontwarn java.awt.font.GlyphVector
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Ellipse2D$Double
-dontwarn java.awt.geom.GeneralPath
-dontwarn java.awt.geom.Line2D$Double
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D$Double
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D$Double
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.lang.invoke.MethodHandleProxies
# JavaCPP's Maven build tooling and Joda's optional conversion annotations.
-dontwarn org.apache.maven.artifact.Artifact
-dontwarn org.apache.maven.model.Build
-dontwarn org.apache.maven.plugin.AbstractMojo
-dontwarn org.apache.maven.plugin.MojoExecutionException
-dontwarn org.apache.maven.plugin.descriptor.PluginDescriptor
-dontwarn org.apache.maven.plugin.logging.Log
-dontwarn org.apache.maven.plugins.annotations.Mojo
-dontwarn org.apache.maven.plugins.annotations.Parameter
-dontwarn org.apache.maven.project.MavenProject
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
