(ns main
  (:require
   [clojure.spec.alpha :as s]
   [org.httpkit.server :refer [run-server]]
   [reitit.ring :as reitit-ring]
   [muuntaja.middleware :refer [wrap-format]]

   [reitit.coercion :refer [compile-request-coercers]]
   [reitit.ring.coercion :refer [coerce-exceptions-middleware
                                 coerce-request-middleware
                                 coerce-response-middleware]]
   [reitit.coercion.spec :refer [coercion]]))

(s/def ::answer int?)

(s/def ::answer-object
  (s/keys :req-un [::answer]))

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
                          :parameters {:body ::answer-object}
                          :responses {200 {:body ::answer-object}}}}]
     ["/echo-bad" {:put {:handler echo-bad
                          :parameters {:body ::answer-object}
                          :responses {200 {:body ::answer-object}}}}]]
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

(defn -main [& _args]
  (run))
