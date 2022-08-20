(ns main
  (:require
   [schema.core :as s]
   [org.httpkit.server :refer [run-server]]
   [reitit.ring :as reitit-ring]
   [muuntaja.middleware :refer [wrap-format]]

   [reitit.coercion :refer [compile-request-coercers]]
   [reitit.ring.coercion :refer [coerce-exceptions-middleware
                                 coerce-request-middleware
                                 coerce-response-middleware]]
   [reitit.coercion.schema :refer [coercion]]))

(def Answer {:answer s/Int})

;; Http server
(defonce server (atom nil))

(defn echo-bad [{{:keys [body]} :parameters}]
  {:status 200
   :body (update body :answer str)})

(defn echo-good [{{:keys [body]} :parameters}]
  {:status 200
   :body body})

(defn toplevel-handler []
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/echo-good" {:put {:handler echo-good
                          :parameters {:body Answer}
                          :responses {200 {:body Answer}}}}]
     ["/echo-bad" {:put {:handler echo-bad
                          :parameters {:body Answer}
                          :responses {200 {:body Answer}}}}]]
    {:data {:coercion coercion
            :compile compile-request-coercers
            :middleware [wrap-format
                         coerce-exceptions-middleware
                         coerce-request-middleware
                         coerce-response-middleware]}})))

(defn run []
  (swap! server (fn [old-server]
                  (when old-server
                    (old-server))
                  (run-server (toplevel-handler) {:port 8081}))))

(run)
