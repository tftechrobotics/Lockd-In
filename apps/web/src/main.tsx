import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/outfit/wght.css';
import '@fontsource-variable/figtree/wght.css';
import './styles/tokens.css';
import './styles/app.css';
import { App } from './App';
import { applyPreviewScene, PREVIEW } from './demo';

if (PREVIEW) applyPreviewScene();

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
