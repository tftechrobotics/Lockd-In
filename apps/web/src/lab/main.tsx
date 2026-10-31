import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/outfit/wght.css';
import '@fontsource-variable/figtree/wght.css';
import '../styles/tokens.css';
import './lab.css';
import { Lab } from './Lab';

const theme = new URLSearchParams(location.search).get('theme');
document.documentElement.dataset.theme = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light';
createRoot(document.getElementById('root')!).render(<StrictMode><Lab /></StrictMode>);
