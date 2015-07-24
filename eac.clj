(ns eac
  (require [clojure.string :as str]))

(def ^:dynamic *enlive* "html")

(defn- sym [s]
  (symbol (str *enlive* "/" s)))

(defn- ->int [s]
  (Integer/parseInt (str/replace s #"\s*" "")))

(defn tokenize
  "Split on whitespace outside of parentheses."
  [css]
  (str/split css #"(?x) (?<!\([\w\s-+])* \s+ (?![-+\w\s]*\))"))

(defn bracketed [s]
  (let [[sel params] (map str/trim (rest (re-matches #"(.+)\((.+)\)" s)))
        sel' (sym sel)]
    (if (= sel "not")
      `(~(sym "but") ~(keyword params))

      (condp = params
        "even" `(~sel' 2 0)
        "odd" `(~sel' 2 1)

        ;; nth-child(an+b) and derivatives
        (if-let [[_ sgn x n y] (re-matches #"(?i)([-+]?)(\d*)(?:(n)\s*([+-]\s*\d+)?)?" params)]
          (let [signed #(str %2 (if (empty? %1) 1 %1))
                a (if n (->int (signed x sgn)) 0)
                b (if y (->int y) (if n 0 (->int (signed x sgn))))]
            `(~sel' ~a ~b)))))))

(defn pseudos [s]
  (if (= s "empty")
    (sym "void")

    (cond
      (some #{s} ["root" "first-child" "last-child" "first-of-type" "last-of-type" "only-child" "only-of-type"])
      (sym s)

      (some #(.startsWith s %) ["nth-child" "nth-last-child" "nth-of-type" "nth-last-of-type" "not"])
      (bracketed s))))

(defn attrs [s]
  (let [attrs (re-seq #"(?x) \[ ([^=\]]+?)  (?: ([~|*$^]?=)([^=\]]+) )? \]" s)]
    (mapv (fn [[_ kw' sgn v]]
           (let [kw (keyword kw')] 
             (if-not sgn
               `(~'attr? ~kw)
               (condp = sgn
                 "=" `(~'attr= ~kw ~v)
                 "~=" `(~'attr-has ~kw ~v)
                 "^=" `(~'attr-starts ~kw ~v)
                 "$=" `(~'attr-ends ~kw ~v)
                 "*=" `(~'attr-contains ~kw ~v)
                 "|=" `(~'attr|= ~kw ~v)
                 nil))))
         attrs)))
  
(defn parse [token]
  (if-let [[_ sel psu] (re-find #"(.*):(.*)" token)]
    (conj [(keyword sel)] (pseudos psu))

    (if-let [[_ sel att] (re-find #"(.+?)(\[.+)" token)]
      (apply conj [(keyword sel)] (attrs att))

      (keyword token))))


(defn translate-css
  "Translates a CSS selector into the equivalent enlive vector form. 
   Any generated references to enlive are namespaced under 'html' by 
   default; pass in a different name if required."
  ([cssrule]
   (mapv parse (tokenize cssrule)))
  ([cssrule enlive]
   (with-bindings {#'*enlive* enlive}
     (translate-css cssrule))))


;; test
(def testcss "p#x > body.blue:not(.red) #lol:first-of-type input[type=text] span:nth-child(3n + 1 )")
(defn testrun [] (translate-css testcss))
;(println (testrun))
