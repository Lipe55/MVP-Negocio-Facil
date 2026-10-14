import { notifySessionChanged } from "../session-sync";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link, useNavigate, Navigate } from "react-router-dom";
import { Building2, Check } from "lucide-react";
import { api, errorMessage } from "../api";
import { loginSchema, registerSchema } from "../schemas";
import { useAuth } from "../auth";
import { Field } from "../components/ui";
import ThemeToggle from "../components/ThemeToggle";
interface Values {
  name: string;
  email: string;
  password: string;
  companyName: string;
  companyPhone: string;
  companyDocument: string;
}
export default function AuthPage({ register = false }: { register?: boolean }) {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const {
    register: field,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<Values>({
    resolver: zodResolver(register ? registerSchema : loginSchema),
    defaultValues: {
      name: "",
      email: "",
      password: "",
      companyName: "",
      companyPhone: "",
      companyDocument: "",
    },
  });
  if (user) return <Navigate to="/dashboard" replace />;
  const submit = handleSubmit(async (values) => {
    try {
      if (register) {
        await api.post("/auth/register", values);
      }
      await api.post("/auth/login", {
        email: values.email,
        password: values.password,
      });
      notifySessionChanged();
      await refresh();
      navigate("/dashboard", { replace: true });
    } catch (e) {
      setError("root", { message: errorMessage(e) });
    }
  });
  return (
    <div className="auth-page">
      <section className="auth-story">
        <Link to="/login" className="brand">
          <Building2 />
          <span>
            negócio<span className="brand-light">fácil</span>
          </span>
        </Link>
        <div>
          <span className="eyebrow">MENOS BUROCRACIA. MAIS NEGÓCIOS.</span>
          <h1>
            Seu próximo negócio
            <br />
            começa com um
            <br />
            bom orçamento.
          </h1>
          <p>
            Organize seus clientes, envie propostas profissionais e acompanhe
            cada resposta em um só lugar.
          </p>
          <ul>
            {[
              "Clientes e propostas organizados",
              "Aprovação por link, sem complicação",
              "Compartilhamento direto pelo WhatsApp",
            ].map((t) => (
              <li key={t}>
                <Check size={17} />
                {t}
              </li>
            ))}
          </ul>
        </div>
        <small>Feito para quem faz o negócio acontecer.</small>
      </section>
      <main className="auth-main">
        <div className="auth-theme"><ThemeToggle /></div>
        <div className="auth-form">
          <span className="eyebrow">NEGÓCIO FÁCIL</span>
          <h1>
            {register
              ? "Comece a organizar seu negócio"
              : "Bom ter você de volta"}
          </h1>
          <p className="muted">
            {register
              ? "Crie sua conta e o espaço da sua empresa."
              : "Entre para acompanhar seus clientes e orçamentos."}
          </p>
          <form onSubmit={submit} noValidate>
            {register && (
              <Field label="Nome do responsável" error={errors.name?.message}>
                <input autoComplete="name" {...field("name")} />
              </Field>
            )}
            <Field label="E-mail" error={errors.email?.message}>
              <input
                type="email"
                autoComplete="email"
                placeholder="voce@empresa.com.br"
                {...field("email")}
              />
            </Field>
            <Field label="Senha" error={errors.password?.message}>
              <input
                type="password"
                autoComplete={register ? "new-password" : "current-password"}
                placeholder={
                  register ? "Pelo menos 10 caracteres" : "Sua senha"
                }
                {...field("password")}
              />
            </Field>
            {register && (
              <>
                <Field
                  label="Nome da empresa"
                  error={errors.companyName?.message}
                >
                  <input
                    autoComplete="organization"
                    {...field("companyName")}
                  />
                </Field>
                <div className="form-grid">
                  <Field
                    label="Telefone da empresa"
                    error={errors.companyPhone?.message}
                  >
                    <input
                      type="tel"
                      autoComplete="tel"
                      placeholder="(11) 99999-9999"
                      {...field("companyPhone")}
                    />
                  </Field>
                  <Field
                    label="CNPJ (opcional)"
                    error={errors.companyDocument?.message}
                  >
                    <input {...field("companyDocument")} />
                  </Field>
                </div>
              </>
            )}
            {errors.root && (
              <p className="form-error" role="alert">
                {errors.root.message}
              </p>
            )}
            <button className="btn w-full" disabled={isSubmitting}>
              {isSubmitting ? "Aguarde…" : register ? "Criar conta" : "Entrar"}
            </button>
          </form>
          <p className="auth-switch">
            {register ? "Já tem uma conta?" : "Ainda não tem uma conta?"}{" "}
            <Link to={register ? "/login" : "/cadastro"}>
              {register ? "Entrar" : "Cadastrar empresa"}
            </Link>
          </p>
          {register && (
            <p className="privacy-note">
              Seus dados serão usados para gerir sua conta e suas propostas.
              Compartilhe apenas as informações necessárias com seus clientes.
            </p>
          )}
        </div>
      </main>
    </div>
  );
}
