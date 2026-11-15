# Acceptance instrumentation only, never the production APK. Truth's compile-time Error Prone
# annotations refer to the JDK compiler model, which Android does not provide. There is no runtime
# call to this annotation element type. Keep the missing-class suppression scoped to that one type.
-dontwarn javax.lang.model.element.Modifier
