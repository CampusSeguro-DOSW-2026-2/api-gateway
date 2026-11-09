// RNF-GW-01: con un backend sano y 50 solicitudes/s durante 60 s, la latencia que agrega el GW
// es <= 50 ms en P95 y no hay 5xx atribuibles al GW.
// Cada iteración llama al doble de MS01 directo y luego a través del GW; "latencia_agregada_gw"
// es la diferencia entre ambas.
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const GW_URL = __ENV.GW_URL || 'http://localhost:8080';
const MS01_URL = __ENV.MS01_URL || 'http://localhost:8081';
const TOKEN = __ENV.TOKEN; // Opcional: con token prueba la ruta protegida (valida el JWT); sin él, la pública.

const PATH = TOKEN ? '/api/v1/users/me' : '/api/v1/auth/login';
const METHOD = TOKEN ? 'GET' : 'POST';
const PARAMS = { headers: TOKEN ? { Authorization: `Bearer ${TOKEN}` } : { 'Content-Type': 'application/json' } };
const BODY = TOKEN ? null : '{"correo":"carga@escuelaing.edu.co","contrasena":"Campus#2026"}';

const addedLatency = new Trend('latencia_agregada_gw', true);

export const options = {
  scenarios: {
    rnf_gw_01: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    latencia_agregada_gw: ['p(95)<=50'],
    'checks{destino:gw}': ['rate==1'],
  },
};

export default function () {
  const direct = http.request(METHOD, `${MS01_URL}${PATH}`, BODY, PARAMS);
  const viaGateway = http.request(METHOD, `${GW_URL}${PATH}`, BODY, PARAMS);
  check(viaGateway, { 'el GW no responde 5xx': (r) => r.status > 0 && r.status < 500 }, { destino: 'gw' });
  addedLatency.add(viaGateway.timings.duration - direct.timings.duration);
}
