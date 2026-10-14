import { notifySessionChanged } from "../session-sync";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Building2, LockKeyhole, Save } from "lucide-react";
import { api, errorMessage, resetCsrf } from "../api";
import { name, phone, password } from "../schemas";
import type { Company } from "../types";
import {
  PageHeader,
  Field,
  Loading,
  ErrorState,
  useNotify,
} from "../components/ui";
import { useAuth } from "../auth";
const companySchema = z.object({
  name,
  phone,
  document: z.string().max(20),
  responsibleName: name,
});
const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Informe a senha atual."),
    newPassword: password,
    confirmation: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmation, {
    path: ["confirmation"],
    message: "As senhas não coincidem.",
  });
function CompanyForm({ company }: { company: Company }) {
  const client = useQueryClient();
  const { refresh } = useAuth();
  const notify = useNotify();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof companySchema>>({
    resolver: zodResolver(companySchema),
    defaultValues: { ...company, document: company.document ?? "" },
  });
  return (
    <section className="panel form-panel">
      <h2>
        <Building2 size={20} />
        Dados da empresa
      </h2>
      <p className="muted">
        Mantenha as informações do seu negócio atualizadas.
      </p>
      <form
        noValidate
        onSubmit={handleSubmit(async (input) => {
          try {
            await api.put("/company", input);
            await client.invalidateQueries({ queryKey: ["company"] });
            await refresh();
            notify("Dados da empresa atualizados.");
          } catch (e) {
            setError("root", { message: errorMessage(e) });
          }
        })}
      >
        <div className="form-grid">
          <Field label="Nome da empresa" error={errors.name?.message}>
            <input {...register("name")} />
          </Field>
          <Field
            label="Nome do responsável"
            error={errors.responsibleName?.message}
          >
            <input {...register("responsibleName")} />
          </Field>
          <Field label="Telefone" error={errors.phone?.message}>
            <input type="tel" {...register("phone")} />
          </Field>
          <Field label="CNPJ (opcional)" error={errors.document?.message}>
            <input {...register("document")} />
          </Field>
        </div>
        {errors.root && (
          <p className="form-error" role="alert">
            {errors.root.message}
          </p>
        )}
        <button className="btn" disabled={isSubmitting}>
          <Save size={16} />
          {isSubmitting ? "Salvando…" : "Salvar alterações"}
        </button>
      </form>
    </section>
  );
}
function PasswordForm() {
  const notify = useNotify();
  const navigate = useNavigate();
  const client = useQueryClient();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
  });
  return (
    <section className="panel form-panel">
      <h2>
        <LockKeyhole size={20} />
        Segurança da conta
      </h2>
      <p className="muted">Confirme sua senha atual para definir uma nova.</p>
      <form
        noValidate
        onSubmit={handleSubmit(async (input) => {
          try {
            await api.post("/auth/password", input);
            notifySessionChanged();
            resetCsrf();
            client.clear();
            client.setQueryData(["session"], null);
            notify("Senha alterada. Entre novamente para continuar.");
            navigate("/login");
          } catch (e) {
            setError("root", { message: errorMessage(e) });
          }
        })}
      >
        <Field label="Senha atual" error={errors.currentPassword?.message}>
          <input
            type="password"
            autoComplete="current-password"
            {...register("currentPassword")}
          />
        </Field>
        <div className="form-grid">
          <Field label="Nova senha" error={errors.newPassword?.message}>
            <input
              type="password"
              autoComplete="new-password"
              {...register("newPassword")}
            />
          </Field>
          <Field
            label="Confirme a nova senha"
            error={errors.confirmation?.message}
          >
            <input
              type="password"
              autoComplete="new-password"
              {...register("confirmation")}
            />
          </Field>
        </div>
        {errors.root && (
          <p className="form-error" role="alert">
            {errors.root.message}
          </p>
        )}
        <button className="btn secondary" disabled={isSubmitting}>
          {isSubmitting ? "Alterando…" : "Alterar senha"}
        </button>
      </form>
    </section>
  );
}
export default function Settings() {
  const query = useQuery({
    queryKey: ["company"],
    queryFn: async () => (await api.get<Company>("/company")).data,
  });
  if (query.isLoading) return <Loading />;
  if (query.error)
    return (
      <ErrorState error={query.error} retry={() => void query.refetch()} />
    );
  return (
    <>
      <PageHeader
        title="Configurações"
        subtitle="Sua empresa e sua conta, do seu jeito."
      />
      <div className="settings-grid">
        <CompanyForm company={query.data!} />
        <PasswordForm />
      </div>
    </>
  );
}
