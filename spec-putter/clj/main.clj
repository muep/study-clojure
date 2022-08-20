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

(def last-req (atom nil))

(defn hello-name [{{{:keys [count name]
                     :or {count 1}} :path} :parameters :as req}]
  (reset! last-req req)
  {:body (apply str (repeat count (str "Hello, " name "!\n")))})

(defn echo-body [{{:keys [body]} :parameters}]
  {:body (assoc body :answer-str (-> body :answer str))})

(defn toplevel-handler []
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/hello/:name" {:get {:handler hello-name
                            :parameters {:path {:name s/Str}}}}]
     ["/hello/:name/:count" {:get {:handler hello-name
                                   :parameters {:path {:name s/Str
                                                       :count s/Int}}}}]
     ["/echo-body" {:put {:handler echo-body
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
