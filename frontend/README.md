# Media Utility Web — Frontend

React + TypeScript frontend for Media Utility Web, built with Vite.

## Stack

- React 18
- TypeScript 5
- Vite 5

## Prerequisites

- Node.js 18 or later
- npm 9 or later

## Getting Started

Install dependencies:

```bash
cd frontend
npm install
```

Start the development server:

```bash
npm run dev
```

The app is served at `http://localhost:5173`.

In development, API requests to `/api/*` are proxied to the backend at `http://localhost:8080`. Start the backend before using API-dependent features.

## Build

Compile and bundle for production:

```bash
npm run build
```

Output is written to `frontend/dist/`.

## Preview

Serve the production build locally:

```bash
npm run preview
```
