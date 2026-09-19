{{/* Resource name: fullnameOverride or the release name. */}}
{{- define "deploykit-app.fullname" -}}
{{- default .Release.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Selector labels. app.kubernetes.io/name is the application name so the backend
(KubernetesService) can find the pods with the same selector whatever created them.
*/}}
{{- define "deploykit-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "deploykit-app.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "deploykit-app.labels" -}}
{{ include "deploykit-app.selectorLabels" . }}
app.kubernetes.io/managed-by: deploykit
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end -}}

{{- define "deploykit-app.secretName" -}}
{{- if .Values.existingSecret -}}{{ .Values.existingSecret }}{{- else -}}{{ include "deploykit-app.fullname" . }}{{- end -}}
{{- end -}}
