import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/outfit/wght.css';
import '@fontsource-variable/figtree/wght.css';
import './styles/tokens.css';
import './styles/app.css';
import { App } from './App';
import { applyPreviewScene, PREVIEW, useDemoVersion } from './demo';
import { previewParams } from './previewParams';

if (PREVIEW) applyPreviewScene();

const root = createRoot(document.getElementById('root')!);
if (PREVIEW && previewParams().get('studio') === '1') {
  // Design preview only (compiled out of the production build): the app inside device frames.
  void import('./preview/Studio').then(({ Studio }) => root.render(<StrictMode><Studio /></StrictMode>));
} else if (PREVIEW) {
  root.render(<StrictMode><PreviewApp /></StrictMode>);
} else {
  root.render(<StrictMode><App /></StrictMode>);
}

/** Preview build: remount when a demo state changes so every screen agrees. */
function PreviewApp() {
  const v = useDemoVersion();
  return <App key={v} />;
}
