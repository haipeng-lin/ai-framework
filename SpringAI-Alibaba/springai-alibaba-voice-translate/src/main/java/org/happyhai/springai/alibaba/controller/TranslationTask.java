package org.happyhai.springai.alibaba.controller;

/**
 * 在请求线程内把 MultipartFile 一次性读成 byte[]，避免跨线程持有 MultipartFile
 * 引用导致 Spring 清理临时存储后内容丢失。
 */
record TranslationTask(String taskId, byte[] audioBytes, String filename, String targetLanguage) {
}
