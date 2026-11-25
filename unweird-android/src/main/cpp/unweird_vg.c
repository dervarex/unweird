// JNI binding for com.dervarex.unweird.AndroidVg: NanoVG on OpenGL ES 3.
// Text arrives as UTF-8 byte arrays (JNI's own string encoding is "modified UTF-8", which breaks emoji).

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <GLES3/gl3.h>

#include "nanovg/nanovg.h"
#define NANOVG_GLES3_IMPLEMENTATION
#include "nanovg/nanovg_gl.h"

#define FN(name) JNICALL Java_com_dervarex_unweird_AndroidVg_##name
#define CTX ((NVGcontext*) (intptr_t) ctx)

JNIEXPORT jlong FN(nCreate)(JNIEnv* env, jclass c) {
    return (jlong) (intptr_t) nvgCreateGLES3(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
}

JNIEXPORT void FN(nDelete)(JNIEnv* env, jclass c, jlong ctx) {
    nvgDeleteGLES3(CTX);
}

JNIEXPORT void FN(nClear)(JNIEnv* env, jclass c, jint width, jint height, jfloat r, jfloat g, jfloat b) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, width, height);
    glClearColor(r, g, b, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
}

JNIEXPORT void FN(nBeginFrame)(JNIEnv* env, jclass c, jlong ctx, jfloat w, jfloat h, jfloat ratio) {
    nvgBeginFrame(CTX, w, h, ratio);
}

JNIEXPORT void FN(nEndFrame)(JNIEnv* env, jclass c, jlong ctx) {
    nvgEndFrame(CTX);
}

JNIEXPORT void FN(nBeginPath)(JNIEnv* env, jclass c, jlong ctx) {
    nvgBeginPath(CTX);
}

JNIEXPORT void FN(nRect)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat w, jfloat h) {
    nvgRect(CTX, x, y, w, h);
}

JNIEXPORT void FN(nRoundedRect)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat w, jfloat h, jfloat r) {
    nvgRoundedRect(CTX, x, y, w, h, r);
}

JNIEXPORT void FN(nCircle)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat r) {
    nvgCircle(CTX, x, y, r);
}

JNIEXPORT void FN(nMoveTo)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y) {
    nvgMoveTo(CTX, x, y);
}

JNIEXPORT void FN(nLineTo)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y) {
    nvgLineTo(CTX, x, y);
}

JNIEXPORT void FN(nHole)(JNIEnv* env, jclass c, jlong ctx) {
    nvgPathWinding(CTX, NVG_HOLE);
}

JNIEXPORT void FN(nFillColor)(JNIEnv* env, jclass c, jlong ctx, jfloat r, jfloat g, jfloat b, jfloat a) {
    nvgFillColor(CTX, nvgRGBAf(r, g, b, a));
}

JNIEXPORT void FN(nStrokeColor)(JNIEnv* env, jclass c, jlong ctx, jfloat r, jfloat g, jfloat b, jfloat a) {
    nvgStrokeColor(CTX, nvgRGBAf(r, g, b, a));
}

JNIEXPORT void FN(nStrokeWidth)(JNIEnv* env, jclass c, jlong ctx, jfloat w) {
    nvgStrokeWidth(CTX, w);
}

JNIEXPORT void FN(nRoundLines)(JNIEnv* env, jclass c, jlong ctx) {
    nvgLineCap(CTX, NVG_ROUND);
    nvgLineJoin(CTX, NVG_ROUND);
}

JNIEXPORT void FN(nBoxGradient)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat f,
                               jfloat r1, jfloat g1, jfloat b1, jfloat a1, jfloat r2, jfloat g2, jfloat b2, jfloat a2) {
    nvgFillPaint(CTX, nvgBoxGradient(CTX, x, y, w, h, r, f, nvgRGBAf(r1, g1, b1, a1), nvgRGBAf(r2, g2, b2, a2)));
}

JNIEXPORT void FN(nImagePattern)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat w, jfloat h, jint image, jfloat alpha) {
    nvgFillPaint(CTX, nvgImagePattern(CTX, x, y, w, h, 0, image, alpha));
}

JNIEXPORT void FN(nFill)(JNIEnv* env, jclass c, jlong ctx) {
    nvgFill(CTX);
}

JNIEXPORT void FN(nStroke)(JNIEnv* env, jclass c, jlong ctx) {
    nvgStroke(CTX);
}

JNIEXPORT void FN(nSave)(JNIEnv* env, jclass c, jlong ctx) {
    nvgSave(CTX);
}

JNIEXPORT void FN(nRestore)(JNIEnv* env, jclass c, jlong ctx) {
    nvgRestore(CTX);
}

JNIEXPORT void FN(nGlobalAlpha)(JNIEnv* env, jclass c, jlong ctx, jfloat alpha) {
    nvgGlobalAlpha(CTX, alpha);
}

JNIEXPORT void FN(nTranslate)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y) {
    nvgTranslate(CTX, x, y);
}

JNIEXPORT void FN(nIntersectScissor)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jfloat w, jfloat h) {
    nvgIntersectScissor(CTX, x, y, w, h);
}

JNIEXPORT void FN(nFont)(JNIEnv* env, jclass c, jlong ctx, jint face, jfloat size, jfloat lineHeight, jint align) {
    nvgFontFaceId(CTX, face);
    nvgFontSize(CTX, size);
    nvgTextLineHeight(CTX, lineHeight);
    nvgTextAlign(CTX, align);
}

JNIEXPORT jfloat FN(nTextAdvance)(JNIEnv* env, jclass c, jlong ctx, jbyteArray utf8) {
    jsize length = (*env)->GetArrayLength(env, utf8);
    jbyte* text = (*env)->GetByteArrayElements(env, utf8, NULL);
    float advance = nvgTextBounds(CTX, 0, 0, (const char*) text, (const char*) text + length, NULL);
    (*env)->ReleaseByteArrayElements(env, utf8, text, JNI_ABORT);
    return advance;
}

JNIEXPORT void FN(nText)(JNIEnv* env, jclass c, jlong ctx, jfloat x, jfloat y, jbyteArray utf8) {
    jsize length = (*env)->GetArrayLength(env, utf8);
    jbyte* text = (*env)->GetByteArrayElements(env, utf8, NULL);
    nvgText(CTX, x, y, (const char*) text, (const char*) text + length);
    (*env)->ReleaseByteArrayElements(env, utf8, text, JNI_ABORT);
}

JNIEXPORT jint FN(nGlyphPositions)(JNIEnv* env, jclass c, jlong ctx, jbyteArray utf8, jintArray offsets, jfloatArray xs, jfloatArray maxXs) {
    jsize length = (*env)->GetArrayLength(env, utf8);
    jsize max = (*env)->GetArrayLength(env, xs);
    if (max <= 0) {
        return 0;
    }
    NVGglyphPosition* glyphs = malloc(sizeof(NVGglyphPosition) * max);
    jint* offsetOut = malloc(sizeof(jint) * max);
    jfloat* xOut = malloc(sizeof(jfloat) * max);
    jfloat* maxXOut = malloc(sizeof(jfloat) * max);
    jbyte* text = (*env)->GetByteArrayElements(env, utf8, NULL);
    int count = nvgTextGlyphPositions(CTX, 0, 0, (const char*) text, (const char*) text + length, glyphs, max);
    for (int i = 0; i < count; i++) {
        offsetOut[i] = (jint) (glyphs[i].str - (const char*) text);
        xOut[i] = glyphs[i].x;
        maxXOut[i] = glyphs[i].maxx;
    }
    (*env)->ReleaseByteArrayElements(env, utf8, text, JNI_ABORT);
    (*env)->SetIntArrayRegion(env, offsets, 0, count, offsetOut);
    (*env)->SetFloatArrayRegion(env, xs, 0, count, xOut);
    (*env)->SetFloatArrayRegion(env, maxXs, 0, count, maxXOut);
    free(glyphs);
    free(offsetOut);
    free(xOut);
    free(maxXOut);
    return count;
}

/** Copies the font data; NanoVG frees the copy with the context. */
JNIEXPORT jint FN(nCreateFont)(JNIEnv* env, jclass c, jlong ctx, jstring name, jbyteArray data) {
    jsize length = (*env)->GetArrayLength(env, data);
    unsigned char* copy = malloc(length);
    if (copy == NULL) {
        return -1;
    }
    (*env)->GetByteArrayRegion(env, data, 0, length, (jbyte*) copy);
    const char* fontName = (*env)->GetStringUTFChars(env, name, NULL);
    int font = nvgCreateFontMem(CTX, fontName, copy, length, 1);
    (*env)->ReleaseStringUTFChars(env, name, fontName);
    return font;
}

JNIEXPORT void FN(nAddFallbackFont)(JNIEnv* env, jclass c, jlong ctx, jint base, jint fallback) {
    nvgAddFallbackFontId(CTX, base, fallback);
}

JNIEXPORT jint FN(nCreateImage)(JNIEnv* env, jclass c, jlong ctx, jbyteArray encoded) {
    jsize length = (*env)->GetArrayLength(env, encoded);
    jbyte* data = (*env)->GetByteArrayElements(env, encoded, NULL);
    int image = nvgCreateImageMem(CTX, 0, (unsigned char*) data, length);
    (*env)->ReleaseByteArrayElements(env, encoded, data, JNI_ABORT);
    return image;
}

JNIEXPORT jint FN(nCreateImageRgba)(JNIEnv* env, jclass c, jlong ctx, jint width, jint height, jbyteArray rgba) {
    jbyte* data = (*env)->GetByteArrayElements(env, rgba, NULL);
    int image = nvgCreateImageRGBA(CTX, width, height, 0, (const unsigned char*) data);
    (*env)->ReleaseByteArrayElements(env, rgba, data, JNI_ABORT);
    return image;
}

JNIEXPORT jintArray FN(nImageSize)(JNIEnv* env, jclass c, jlong ctx, jint image) {
    int size[2] = {0, 0};
    nvgImageSize(CTX, image, &size[0], &size[1]);
    jintArray result = (*env)->NewIntArray(env, 2);
    (*env)->SetIntArrayRegion(env, result, 0, 2, (const jint*) size);
    return result;
}

JNIEXPORT void FN(nDeleteImage)(JNIEnv* env, jclass c, jlong ctx, jint image) {
    nvgDeleteImage(CTX, image);
}

/** An OpenGL texture (premultiplied, bottom row first) as a NanoVG image; the caller keeps owning the texture. */
JNIEXPORT jint FN(nImageFromTexture)(JNIEnv* env, jclass c, jlong ctx, jint texture, jint width, jint height) {
    return nvglCreateImageFromHandleGLES3(CTX, (GLuint) texture, width, height,
                                          NVG_IMAGE_NODELETE | NVG_IMAGE_FLIPY | NVG_IMAGE_PREMULTIPLIED);
}
