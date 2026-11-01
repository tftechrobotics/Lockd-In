import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/outfit/wght.css';
import '@fontsource-variable/figtree/wght.css';
import './styles/tokens.css';
import './styles/app.css';
import { App } from './App';
import { applyPreviewScene, PREVIEW } from './demo';

if (PREVIEW) applyPreviewScene();

const root = createRoot(document.getElementById('root')!);
if (PREVIEW && new URLSearchParams(location.search).get('sheet') === '1') {
  // Design preview only: the component sheet (compiled out of the production build).
  void import('./preview/Sheet').then(({ Sheet }) => root.render(<StrictMode><Sheet /></StrictMode>));
} else {
  root.render(<StrictMode><App /></StrictMode>);
}
