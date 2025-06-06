package main

import (
	"flag"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
)

func main() {
	listenAddr := flag.String("listen", ":8080", "Address to listen on (e.g. :8080)")
	upstream := flag.String("upstream", "", "Upstream server URL (e.g. http://example.com)")
	flag.Parse()

	log := slog.Default()

	if *upstream == "" {
		log.Error("--upstream flag is required")
		os.Exit(1)
	}

	upstreamURL, err := url.Parse(*upstream)
	if err != nil {
		log.Error("Invalid upstream URL", "error", err)
		os.Exit(1)
	}

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		log.Info("Request", "method", r.Method, "url", r.URL.String())
		addCORSHeaders(w)

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		proxyReq, err := http.NewRequest(r.Method, upstreamURL.String()+r.RequestURI, r.Body)
		if err != nil {
			w.WriteHeader(http.StatusInternalServerError)
			log.Error("Error creating proxy request", "error", err)
			return
		}
		proxyReq.Header = r.Header.Clone()

		resp, err := http.DefaultClient.Do(proxyReq)
		if err != nil {
			w.WriteHeader(http.StatusBadGateway)
			w.Write([]byte(err.Error()))
			log.Error("Error proxying request", "error", err)
			return
		}
		defer resp.Body.Close()

		for k, vv := range resp.Header {
			for _, v := range vv {
				w.Header().Add(k, v)
			}
		}
		addCORSHeaders(w) // Ensure CORS headers are not overwritten
		w.WriteHeader(resp.StatusCode)
		_, err = io.Copy(w, resp.Body)
		if err != nil {
			log.Error("Error copying response body", "error", err)
		}
	})

	log.Info("Listening", "listenAddr", *listenAddr, "upstreamURL", upstreamURL.String())
	log.Error("Error listening", "error", http.ListenAndServe(*listenAddr, nil))
}

func addCORSHeaders(w http.ResponseWriter) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "*")
	w.Header().Set("Access-Control-Allow-Headers", "*")
}
