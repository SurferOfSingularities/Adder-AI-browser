# Project: Adder

## Purpose

Adder is an AI-powered ad-blocking browser for Android and iOS. It uses on-device LLMs to intelligently identify and remove advertising-related HTML elements from web pages, going beyond simple domain blocking to understand content relevance.

## Architecture Overview

Kotlin Multiplatform (KMP) project with three modules:

- **shared/** — Common business logic: heuristic ad detection, LLM prompt construction, DOM element modeling, pipeline orchestration. All platform-agnostic code lives here.
- **androidApp/** — Android application with WebView-based browser UI and Gemini Nano LLM integration.
- **iosApp/** — iOS application (Swift) with WKWebView-based browser UI and Apple Foundation Models LLM integration.

## Target Platforms

- **Android:** minSdk 34 (Android 14+). Gemini Nano requires Pixel 8+ or Samsung S24+ devices.
- **iOS:** iOS 26+. Apple Foundation Models requires iPhone 15 Pro+ or M-series iPad/Mac.

## How It Works

1. User navigates to a URL in the built-in browser.
2. Page loads normally in the platform WebView.
3. On page load complete, JavaScript is injected to extract DOM element metadata.
4. Heuristic rules (fast, shared code) classify obvious ads for immediate removal.
5. Ambiguous elements are sent to the on-device LLM for intelligent classification.
6. Identified ads are removed from the DOM via JavaScript injection.
7. User sees the cleaned page with ads removed (post-render cleanup approach).
