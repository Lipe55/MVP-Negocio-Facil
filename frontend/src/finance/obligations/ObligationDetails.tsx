import { useQuery } from '@tanstack/react-query';
import { api } from '../../api';
import { money } from '../../money';
import { Empty, ErrorState, Loading, Modal } from '../../components/ui';
import InstallmentTable from './InstallmentTable';
import type { InstallmentActions } from './InstallmentTable';
import type { ObligationDetail } from './types';

export default function ObligationDetails({ id, actions, onClose }: { id: number; actions: InstallmentActions; onClose: () => void }) {
  const query = useQuery({ queryKey: ['finance', 'obligation', id], queryFn: async ({ signal }) => (await api.get<ObligationDetail>(`/finance/obligations/${id}`, { signal })).data });
  return <Modal title="Parcelas da obrigação" onClose={onClose}>
    <div className="obligation-details">
      {query.isLoading ? <Loading /> : query.error ? <ErrorState error={query.error} retry={() => void query.refetch()} /> : query.data && <>
        <h3>{query.data.description}</h3><p className="finance-note">Valor total: <strong>{money(query.data.totalAmount)}</strong> · {query.data.installmentCount} parcela(s). Cada pagamento pertence à sua respectiva parcela.</p>
        {query.data.installments.length ? <InstallmentTable entries={query.data.installments} actions={actions} showDetails={false} /> : <Empty title="Nenhuma parcela encontrada">Não há parcelas disponíveis para esta obrigação.</Empty>}
      </>}
      <div className="actions"><button className="btn secondary" onClick={onClose}>Fechar parcelas</button></div>
    </div>
  </Modal>;
}
