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

(def last-req (atom nil))

(defn hello-name [{{{:keys [count name]
                     :or {count 1}} :path} :parameters :as req}]
  (reset! last-req req)
  {:body (apply str (repeat count (str "Hello, " name "!\n")))})

(defn echo-body [{{:keys [body]} :parameters}]
  {:body body})

(defn toplevel-handler []
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/hello/:name" {:get {:handler hello-name
                            :parameters {:path {:name string?}}}}]
     ["/hello/:name/:count" {:get {:handler hello-name
                                   :parameters {:path {:name string?
                                                       :count int?}}}}]
     ["/echo-body" {:put {:handler echo-body
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

(run)
