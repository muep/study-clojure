(ns main
  (:require
   [clojure.spec.alpha :as s]
   [org.httpkit.server :refer [run-server]]
   [reitit.ring.middleware.dev :refer [print-request-diffs]]
   [reitit.ring :as reitit-ring]
   [muuntaja.middleware :refer [wrap-format]]

   [reitit.coercion.spec :refer [coercion]]
   [reitit.ring.coercion :refer [coerce-exceptions-middleware
                                 coerce-request-middleware
                                 coerce-response-middleware]]))

;; Data model
(s/def ::title string?)
(s/def ::description string?)
(s/def ::version int?)

(s/def ::foo
  (s/keys :req-un [::title ::description ::version]))

(s/def ::foo-update
  (s/keys :req-opt [::title ::description]))

(defonce foo (atom {:title "foo0"
                    :description "The initial description of foo"
                    :version 0}))

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
                          :parameters {:body int?}}}]]
    {:reitit.middleware/transform print-request-diffs
     :data {:coercion coercion
            :middleware [coerce-exceptions-middleware
                         wrap-format
                         coerce-request-middleware]}})))

(defn run []
  (swap! server (fn [old-server]
                  (when old-server
                    (old-server))
                  (run-server (toplevel-handler) {:port 8081}))))

(run)
