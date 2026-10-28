export const DEFAULT_ROOT = { method: 'bisection', fx: 'x^3 - x - 2', a: '1', b: '2', x0: '1.5', s0: '1', s1: '2', tol: '1e-5', maxIter: '100' };
export const DEFAULT_INTERP = {
  method: 'lagrange',
  rows: [{ id: 1, x: '0', y: '0' }, { id: 2, x: '1', y: '0.8415' }, { id: 3, x: '2', y: '0.9093' }, { id: 4, x: '3', y: '0.1411' }],
  xEval: '1.5', truth: 'fn', fx: 'sin(x)', yTrue: '',
};
export const PRESETS = [
  { label: 'x³ − x − 2', fx: 'x^3 - x - 2', a: '1', b: '2', x0: '1.5', s0: '1', s1: '2' },
  { label: 'cos x − x', fx: 'cos(x) - x', a: '0', b: '1', x0: '0.5', s0: '0', s1: '1' },
  { label: 'eˣ − 3x', fx: 'exp(x) - 3x', a: '0', b: '1', x0: '0.5', s0: '0', s1: '1' },
  { label: 'ln x + x − 2', fx: 'ln(x) + x - 2', a: '1', b: '2', x0: '1.5', s0: '1', s1: '2' },
  { label: 'x² − 2', fx: 'x^2 - 2', a: '1', b: '2', x0: '1.5', s0: '1', s1: '2' },
];
export const METHOD_LABEL = { bisection: 'Bisection', newton: 'Newton-Raphson', secant: 'Secant', lagrange: 'Lagrange', newtondd: 'Newton divided difference' };
export const METHOD_SHORT = { bisection: 'Bisection', newton: 'Newton', secant: 'Secant', lagrange: 'Lagrange', newtondd: 'Newton DD' };
